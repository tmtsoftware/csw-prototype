package csw.services.pkg

import csw.services.loc.LocationService
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.scalatest.Matchers
import akka.remote.testkit.MultiNodeSpecCallbacks

/**
 * For sbt-multi-jvm plugin: See src/multi-jvm in this project: Hooks up MultiNodeSpec with ScalaTest
 */
trait STMultiNodeSpec extends MultiNodeSpecCallbacks
    with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()
}
