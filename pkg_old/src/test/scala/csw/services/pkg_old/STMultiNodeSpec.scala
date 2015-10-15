package csw.services.pkg_old

import org.scalatest.{ DoNotDiscover, BeforeAndAfterAll, WordSpecLike, Matchers }
import akka.remote.testkit.MultiNodeSpecCallbacks

/**
 * Hooks up MultiNodeSpec with ScalaTest
 */
@DoNotDiscover
trait STMultiNodeSpec extends MultiNodeSpecCallbacks
    with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()
}
