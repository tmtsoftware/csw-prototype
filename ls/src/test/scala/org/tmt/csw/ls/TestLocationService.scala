package org.tmt.csw.ls

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import com.typesafe.scalalogging.slf4j.Logging
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.tmt.csw.ls.LocationService.{HCD, ServiceId}
import akka.pattern.ask
import org.tmt.csw.ls.LocationServiceActor.{QueryResult, LocationServiceInfo}
import akka.util.Timeout
import scala.util.{Failure, Success}
import scala.concurrent.{Await, ExecutionContext}
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.tmt.csw.ls.LocationService.ServiceId
import org.tmt.csw.ls.LocationServiceActor.QueryResult
import scala.util.Success
import scala.util.Failure
import scala.Some
import org.tmt.csw.ls.LocationServiceActor.LocationServiceInfo

/**
 * Tests the location service
  */
class TestLocationService extends TestKit(ActorSystem(LocationService.locationServiceName))
with ImplicitSender with FunSuite with Logging with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    system.actorOf(Props[LocationServiceActor], LocationService.locationServiceName)
  }

  test("Test location service") {
    val serviceId = ServiceId("TestActor", HCD)
    val actorRef = system.actorOf(Props(classOf[TestActor], serviceId))
    val duration = 2.seconds
    implicit val timeout = Timeout(2.seconds)

    // Test Get with valid service id
    Await.result(actorRef ? "testGet", duration).asInstanceOf[Option[LocationServiceInfo]] match {
      case Some(LocationServiceInfo(svcId, actorPath, configPaths)) =>
        assert(svcId == serviceId)
        assert(configPaths == Set("a", "b"))
      case None =>
        fail("Location service did not find TestActor")
    }

    // Test Get with invalid service id
    Await.result(actorRef ? "testGetInvalid", duration).asInstanceOf[Option[LocationServiceInfo]] match {
      case s@Some(LocationServiceInfo(svcId, actorPath, configPaths)) =>
        fail(s"Location service found nonexisting actor: $s")
      case None =>
    }

    // Test Query with valid service id
    val queryResult = Await.result(actorRef ? "testQuery", duration).asInstanceOf[QueryResult]
    assert(queryResult.results.size == 1)
    queryResult.results(0) match {
      case LocationServiceInfo(svcId, actorPath, configPaths) =>
        assert(svcId == serviceId)
        assert(configPaths == Set("a", "b"))
    }

    // Test Query with unknown service id
    val queryResult2 = Await.result(actorRef ? "testQueryUnknown", duration).asInstanceOf[QueryResult]
    assert(queryResult2.results.size == 0)

    // Test Query with wildcard
    val queryResult3 = Await.result(actorRef ? "testQueryWildcard", duration).asInstanceOf[QueryResult]
    assert(queryResult.results.size == 1)
    queryResult.results(0) match {
      case LocationServiceInfo(svcId, actorPath, configPaths) =>
        assert(svcId == serviceId)
        assert(configPaths == Set("a", "b"))
    }
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}

// Test actor: We need an actor in order to access the location service
class TestActor(serviceId: ServiceId) extends Actor with ActorLogging {
  LocationService.register(context, serviceId, self, Set("a", "b"))

  override def receive: Receive = {
    case "testGet" =>
      val replyTo = sender
      LocationService.get(context, serviceId).onComplete {
        case Success(x) =>
          replyTo ! x
        case Failure(ex) =>
          log.error("test get failed", ex)
          replyTo ! None
      }

    case "testGetInvalid" =>
      val replyTo = sender
      LocationService.get(context, ServiceId("Invalid", HCD)).onComplete {
        case Success(x) =>
          replyTo ! x
        case Failure(ex) =>
          log.error("test invalid get failed", ex)
          replyTo ! None
      }

    case "testQuery" =>
      val replyTo = sender
      LocationService.query(context, Some(serviceId.name), Some(serviceId.serviceType)).onComplete {
        case Success(x) =>
          replyTo ! x
        case Failure(ex) =>
          log.error("test query failed", ex)
          replyTo ! QueryResult(List())
      }

    case "testQueryUnknown" =>
      val replyTo = sender
      LocationService.query(context, Some("XXX"), Some(serviceId.serviceType)).onComplete {
        case Success(x) =>
          replyTo ! x
        case Failure(ex) =>
          log.error("test query failed", ex)
          replyTo ! QueryResult(List())
      }

    case "testQueryWildcard" =>
      val replyTo = sender
      LocationService.query(context, None, Some(serviceId.serviceType)).onComplete {
        case Success(x) =>
          replyTo ! x
        case Failure(ex) =>
          log.error("test query failed", ex)
          replyTo ! QueryResult(List())
      }
  }
}



