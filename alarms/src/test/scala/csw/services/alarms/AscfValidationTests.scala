package csw.services.alarms

import java.nio.file.Paths

import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.alarms.AscfValidation.Problem
import org.scalatest.FunSuite

/**
 * Tests validating the Alarm Service Config File
 */
class AscfValidationTests extends FunSuite with LazyLogging {
  // Get the test alarm service config file (ascf)
  val url = getClass.getResource("/test-alarms.conf")
  val ascf = Paths.get(url.toURI).toFile

  test("Test validating the alarm service config file") {
    val problems = AscfValidation.validate(ascf)
    problems.foreach(p => println(p.toString))
    assert(Problem.errorCount(problems) == 0)
  }
}
