package freethemonads.example

import Console.functions.Console, ConsoleOps._
import scalaz.{ ~>, Id }
import scalaz.State

object ConsoleCompile {
  type Lists = (List[String], List[String])
  type ListState[A] = State[Lists, A]

  /** Interpreter that writes outputs to a list and takes input from another list. */
  object toListState extends (ConsoleOp ~> ListState) {
    override def apply[A](fa: ConsoleOp[A]): ListState[A] = fa match {
      case Println(text) ⇒
        for {
          v ← State.get[Lists]
          (ins, outs) = v
          _ ← State.put((ins, outs :+ text))
        } yield ()
      case Print(text) ⇒
        for {
          v ← State.get[Lists]
          (ins, outs) = v
          _ ← State.put((ins, outs :+ text))
        } yield ()
      case Readln() ⇒
        for {
          v ← State.get[Lists]
          (ins, outs) = v
          _ ← State.put((ins.tail, outs))
        } yield (ins.head)
    }
  }

  /** Interpreter that reads from sysin and writes to sysout directly (side-effect). */
  object toId extends (ConsoleOp ~> Id.Id) {
    override def apply[A](fa: ConsoleOp[A]) = fa match {
      case Println(text) ⇒ scala.Console.println(text)
      case Print(text) ⇒ scala.Console.print(text)
      case Readln() ⇒ scala.io.StdIn.readLine()
    }
  }
}
