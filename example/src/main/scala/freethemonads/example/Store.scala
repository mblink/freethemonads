package freethemonads.example

import freethemonads.{ addLiftingFunctions, addComposingFunctions }

object StoreOps {
  sealed trait StoreOp[+A]
  case class Put(key: String, value: String) extends StoreOp[Unit]
  case class Get(key: String) extends StoreOp[Option[String]]
}

object Store {
  @addLiftingFunctions[StoreOps.StoreOp]('Store) object functions
  @addComposingFunctions[StoreOps.StoreOp]('Store) object composing
}
