package csw.services.asconsole

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, ConfigResolveOptions}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.asconsole.AlarmUtils.Problem
import csw.services.loc.LocationService
import csw.services.trackLocation.TrackLocation
import org.scalatest.FunSuiteLike
import redis.RedisClient

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


object AsConsoleTests {
  LocationService.initInterface()
  private val system = ActorSystem("Test")
}

/**
  * Created by abrighto on 26/06/16.
  */
class AsConsoleTests extends TestKit(AsConsoleTests.system) with FunSuiteLike with LazyLogging  {
  implicit val sys = AsConsoleTests.system

  import system.dispatcher

  implicit val timeout = Timeout(60.seconds)

  // Get the test alarm service config file (ascf)
  val url = getClass.getResource("/test-alarms.conf")
  val ascf = Paths.get(url.toURI).toFile

  test("Test validating the alarm service config file") {
    val inputConfig = ConfigFactory.parseFile(ascf).resolve(ConfigResolveOptions.noSystem())
    val jsonSchema = ConfigFactory.parseResources("alarms-schema.conf")
    val problems = AlarmUtils.validate(inputConfig, jsonSchema, ascf.getName)
    problems.foreach(p => println(p.toString))
    assert(Problem.errorCount(problems) == 0)
  }

  test("Test initializing the alarm service and then listing the alarms") {
    // Start redis and register it with the location service on port 7777.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Alarm Service Test" --command "redis-server --port 7777" --port 7777
    val asName = "Alarm Service Test"
    val port = 7777
    Future {
      TrackLocation.main(Array("--name", asName, "--command", s"redis-server --port $port", "--port", port.toString))
    }

    // Later, in another JVM, run the asconsole command to initialize the Redis database from the alarm service config file
    val redisClient = RedisClient("localhost", port) // only for testing
    try {
      AsConsole.main(Array("--as-name", asName, "--init", ascf.getAbsolutePath))

      // List the alarms that were written to Redis
      val alarms = Await.result(AlarmUtils.getAlarms(redisClient), timeout.duration)
      alarms.foreach { alarm ⇒
        // XXX TODO: compare results
        println(alarm)
      }
    } finally {
      // Shutdown Redis
      val f = redisClient.shutdown()
      redisClient.stop()
      Await.ready(f, timeout.duration)
    }
  }
}
