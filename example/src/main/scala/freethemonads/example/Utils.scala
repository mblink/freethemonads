package freethemonads.example

import scala.language.higherKinds
import scalaz.Monad
import scalaz.std.list._
import scalaz.syntax.foldable._

object Utils {
  def repeat[M[_]: Monad](times: Int)(fa: M[_]) = List.fill(times)(fa).sequence_
  def iterateUntil[M[_]: Monad, A](pred: A ⇒ Boolean)(fa: M[A]): M[A] =
    Monad[M].bind(fa)(y ⇒ if (pred(y)) Monad[M].pure(y) else iterateUntil(pred)(fa))
}
