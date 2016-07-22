package freethemonads

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.macros.whitebox


/**
  * Usage:
  * <pre>
  * sealed trait Op[+A]
  * case class MyOp(a: String) extends Op[Unit]
  * &commat;addLiftingFunctions[Op]('Mon) object monadic
  * import monadic._
  * val a: Mon[Unit] = myOp("hello")
  * </pre>
  *
  * @tparam Op sealed trait of the Operations
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class addLiftingFunctions[Op[_]](typeName: Symbol) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Macro.addLiftFunctionsAnnotation_impl
}

/**
  * Usage:
  * <pre>
  * sealed trait Op[+A]
  * case class MyOp(a: String) extends Op[Unit]
  * &commat;addComposingFunctions[Op]('Mon) object monadic
  * import monadic._
  * def p[F[_] : Op] = myOp("hello")
  * </pre>
  *
  * @tparam Op sealed trait of the Operations
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class addComposingFunctions[Op[_]](typeName: Symbol) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Macro.addComposingFunctionsAnnotation_impl
}

/**
  * Only use if the annoatation (addLiftingFunctions) is impossible to use.
  *
  * Usage:
  * <pre>
  * sealed trait Op[+A]
  * case class MyOp(a: String) extends Op[Unit]
  * val monadic = FreeMacro.liftFunctions[Op]('Mon)
  * import monadic._
  * val a: Mon[Unit] = myOp("hello")
  * </pre>
  */
object Macro {
  def liftFunctions[F[_]](typeName: Symbol): Any = macro Macro.liftFunctions_impl[F]
}


//Private stuff below

class Macro(val c: whitebox.Context) {
  import c.universe._
  import Describe._

  def liftFunctions_impl[F[_]](typeName: Expr[Any])(implicit t: c.WeakTypeTag[F[_]]) = {
    val desc = Describe(t.tpe.typeSymbol)
    val alias = macroParameter(typeName.tree)

    anonClass(typeAlias(alias, desc) ::
      desc.ops.map(liftedFunction(alias, _)))
  }

  def addLiftFunctionsAnnotation_impl(annottees: Expr[Any]*) = {
    val (opBase, alias) = parseAnnotation
    val desc = Describe(opBase)

    modifyObjectOrClass(annottees,
      typeAlias(alias, desc) ::
        desc.ops.map(liftedFunction(alias, _)))
  }


  def addComposingFunctionsAnnotation_impl(annottees: Expr[Any]*) = {
    val (opBase, alias) = parseAnnotation
    val desc = Describe(opBase)

    val importHigherKinds =
      q"import scala.language.higherKinds"
    val typeAlias =
      q"type $alias[F[_]] = _root_.scalaz.Inject[${desc.opBase.typeSymbol}, F]"

    def function(op: Op) = {
      val paramNames = op.params.map(_.name)
      val paramDefs = op.params.map { p ⇒ q"${p.name}: ${p.tpe}" }
      q"""def ${op.functionName}[F[_] : $alias](..$paramDefs): _root_.scalaz.Free[F, ${op.opA}] =
          _root_.freethemonads.Compose.lift(${op.companion}(..$paramNames))"""
    }

    modifyObjectOrClass(annottees,
      importHigherKinds ::
        typeAlias ::
        desc.ops.map(function))
  }


  private def anonClass(of: List[Tree]): Tree = q"new {..$of}"

  private def modifyObjectOrClass(annottees: Traversable[Expr[Any]], toAdd: List[Tree]): Expr[Any] = {
    val mod = annottees.map(_.tree).toList match {
      case ClassDef(mods, name, tparams, Template(parents, self, body)) :: rest ⇒ //class/trait
        val (initBody, restBody) = body.splitAt(1)
        val t2 = Template(parents, self, initBody ++ toAdd ++ restBody)
        ClassDef(mods, name, tparams, t2) :: rest
      case ModuleDef(mods, name, Template(parents, self, body)) :: rest ⇒ // object
        val t2 = Template(parents, self, toAdd ++ body)
        ModuleDef(mods, name, t2) :: rest
      case a :: rest ⇒
        c.abort(c.enclosingPosition, "Annotation only supported on classes and objects")
    }
    c.Expr(q"..$mod")
  }

  private def parseAnnotation: (Symbol, TypeName) = {
    val q"new $_[$opIdent](${typeName: Tree}).macroTransform(..$_)" = c.macroApplication
    val opBase = c.typecheck(q"???.asInstanceOf[$opIdent[Unit]]").tpe.typeSymbol
    val alias = macroParameter(typeName)
    (opBase, alias)
  }
  private def macroParameter(tree: Tree) = {
    val Apply(_, Literal(Constant(typeName: String)) :: Nil) = tree
    TypeName(typeName)
  }

  private def typeAlias(name: TypeName, desc: Description): Tree = {
    q"type $name[A] = _root_.scalaz.Free[${desc.opBase.typeSymbol}, A]"
  }

  private def liftedFunction(typeAlias: TypeName, op: Op): Tree = {
    val paramNames = op.params.map(_.name)
    val paramDefs = op.params.map { p ⇒ q"${p.name}: ${p.tpe}" }
    q"""def ${op.functionName}(..$paramDefs): $typeAlias[${op.opA}] =
          _root_.scalaz.Free.liftF(${op.companion}(..$paramNames))"""
  }

  /** Describes an operation hierarchy. */
  private object Describe {
    case class Description(opBase: Type, ops: List[Op])
    case class Op(name: TypeName, companion: Symbol, opA: Type, params: List[Field]) {
      /** myOperation for MyOperation */
      def functionName = {
        val className = name.toString
        TermName(className.head.toLower + className.tail)
      }
    }
    case class Field(name: TermName, tpe: Type)

    def apply(opBase: Type): Description = apply(opBase.typeSymbol)

    def apply(opBase: Symbol): Description = {
      val opBaseClass = opBase.asClass

      if (!opBaseClass.isSealed)
        c.abort(c.enclosingPosition, s"The base class ${opBase.name} of the free monad is not sealed")

      val opBaseSubclasses = {
        if (opBaseClass.knownDirectSubclasses.nonEmpty) opBaseClass.knownDirectSubclasses
        else {
          // This is most likely because those classes haven't yet been compiled. So look them up ourselves by
          // assuming they are in the same object that defined the op itself.
          val subclasses = opBaseClass.owner.typeSignature.decls.filter(d ⇒ d.isClass &&
            d.asClass.baseClasses.contains(opBase) && d != opBase)
          if (subclasses.nonEmpty) subclasses
          else {
            c.abort(c.enclosingPosition, s"The base class ${opBase.name} of the free monad has no subclasses. " +
              s"This might be a compilation order problem. To work around it define the operations in the same " +
              s"object where you also defined their parent")
          }
        }
      }
      val ops = opBaseSubclasses.toList.map { case s: ClassSymbol ⇒ describeOp(s, opBaseClass) }

      Description(opBase.asType.toType, ops)
    }

    /** Describes a "case class MyOp(text: String) extends Op[Unit]" */
    private def describeOp(opClass: ClassSymbol, opBase: ClassSymbol): Op = {
      val name = opClass.name
      val companion = opClass.companion
      val a = opClass.typeSignature.baseType(opBase.asType).typeArgs.head
      val params = caseClassFields(opClass.typeSignature)
      Op(name, companion, a, params.toList)
    }

    /** Extracts [Field(text, String), Field(number, Int)] from a "case class MyClass(text: String, number: Int)" */
    private def caseClassFields(tpe: Type): Iterable[Field] = {
      tpe.decls.collect {
        case accessor: MethodSymbol if accessor.isCaseAccessor ⇒
          accessor.typeSignature match {
            case NullaryMethodType(returnType) ⇒ Field(accessor.name, returnType)
          }
      }
    }
  }
}
