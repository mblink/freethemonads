import scala.language.higherKinds
import scalaz.~>

package object freethemonads {
  implicit class TransformationOps[F[_], H[_]](val f: F ~> H) {
    def ||[G[_]](g: G ~> H) = new CombinedTransformation[F, G, H](f, g)
  }
}
