package org.tmt.csw.cs.spray

import spray.routing.Directives
import spray.http.MediaTypes._
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import org.tmt.csw.cs.akka.ConfigServiceActor
import akka.pattern.ask
import org.tmt.csw.cs.core.{ConfigString, GitConfigId}
import org.tmt.csw.cs.api.{ConfigData, ConfigId}
import java.io.File
import akka.util.Timeout
import scala.concurrent.duration._

/**
 * A Spray based HTTP interface to the ConfigServiceActor.
 */
object ConfigService {
  def apply(configServiceActor: ActorRef)(implicit executionContext: ExecutionContext): ConfigService = {
    new ConfigService(configServiceActor)(executionContext)
  }
}

class ConfigService(configServiceActor: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives with DefaultJsonFormats {

  import ConfigServiceActor._
  import spray.httpx.SprayJsonSupport._

  implicit val createRequestFormat = jsonFormat3(CreateRequest)
  implicit val getRequestFormat = jsonFormat2(GetRequest)
//  implicit val existsRequestFormat = jsonFormat1(ExistsRequest)
//  implicit val deleteRequestFormat = jsonFormat2(DeleteRequest)
//  implicit val listRequestFormat = jsonObjectFormat[ListRequest.type]
//  implicit val historyRequestFormat = jsonFormat1(HistoryRequest)

  // XXX
  val tmp = CreateRequest(new File("/x/y/z"), ConfigString("The xyz contents"), "The xyz comment")
  println("XXX CreateRequest = " + createRequestFormat.write(tmp).toString())


  implicit val timeout = Timeout(5.seconds)

  val route =
    path("create") {
      post {
        entity(as[CreateRequest]) {
          createRequest => {
            respondWithMediaType(`application/json`) {
              complete {
                (configServiceActor ? createRequest).mapTo[ConfigId]
              }
            }
          }
        }
      }
    } ~
      // Get the contents of the file given by the path
      path("get" / Rest) {
        filePath =>
          get {
            parameter('id ?) {
              optionalVersionId =>
                respondWithMediaType(`application/json`) {
                  complete {
                    (configServiceActor ? GetRequest(new File(filePath), getConfigId(optionalVersionId))).mapTo[Option[ConfigData]]
                  }
                }
            }
          }
      }

  // Converts Option[String] to Option[ConfigId]
  private def getConfigId(idOpt: Option[String]): Option[ConfigId] = {
    idOpt match {
      case Some(id) => Some(GitConfigId(id))
      case None => None
    }
  }

}
