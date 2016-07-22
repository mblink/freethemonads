package freethemonads.example

import freethemonads.Macro._
import freethemonads.{ addComposingFunctions, addLiftingFunctions }

object ConsoleOps {
  sealed trait ConsoleOp[+A]
  case class Println(text: String) extends ConsoleOp[Unit]
  case class Print(text: String) extends ConsoleOp[Unit]
  case class Readln() extends ConsoleOp[String]
}

object Console {
  @addLiftingFunctions[ConsoleOps.ConsoleOp]('Console) object functions
  @addComposingFunctions[ConsoleOps.ConsoleOp]('Console) object composing
}

object Console_val {
  val functions = liftFunctions[ConsoleOps.ConsoleOp]('Console)
}
