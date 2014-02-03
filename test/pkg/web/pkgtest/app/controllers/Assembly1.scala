package controllers

import play.api._
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import models._
import models.Constraints._
import models.Assembly1Settings._
import play.api.Play.current
import play.api.libs.ws.{Response, WS}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.i18n.Messages
import play.api.cache.Cache
import play.api.libs.json._
import play.api.libs.iteratee.{Enumerator, Iteratee}
import java.util.UUID

/**
 * Demo controller that defines a form where the user can enter values that are
 * sent to the Assembly1 Spray/REST HTTP server in JSON format. The result of a submit
 * to the Assembly1 service is a runId, which is displayed below the form.
 * This class uses long polling to the REST service to get the status of a running command
 * and then uses Javascript and a Websocket  to push the status to the web page, where it
 * is displayed below the form.
 */
object Assembly1 extends Controller {

  // assembly1 command service host and port
  val host = Play.application.configuration.getString("assembly1-http.interface").get
  val port = Play.application.configuration.getString("assembly1-http.port").get

  // URL to use to post a submit command
  val submitUrl = s"http://$host:$port/queue/submit"

  // URL to use to post a get command (to query the current values)
  val getUrl = s"http://$host:$port/get"

  // URL to check the status for the given runId
  def statusUrl(runId: String) = s"http://$host:$port/config/$runId/status"

  // Used to cache form data
  val cacheKey = "assembly1-form-data-" + UUID.randomUUID().toString


  // Form description matching the model class: Assembly1Settings
  private val assembly1SettingsForm: Form[Assembly1Settings] = Form(
    mapping(
      "basePos" -> mapping(
        "posName" -> nonEmptyText,
        "c1" -> nonEmptyText.verifying(hhmmssConstraint),
        "c2" -> nonEmptyText.verifying(ddmmssConstraint),
        "equinox" -> nonEmptyText.verifying(equinoxConstraint)
      )(BasePos.apply)(BasePos.unapply),
      "aoPos" -> mapping(
        "c1" -> nonEmptyText.verifying(hhmmssConstraint),
        "c2" -> nonEmptyText.verifying(ddmmssConstraint),
        "equinox" -> nonEmptyText.verifying(equinoxConstraint)
      )(TelescopePos.apply)(TelescopePos.unapply)
    )(Assembly1Settings.apply)(Assembly1Settings.unapply)
  ).fill(Assembly1Settings.defaultSettings)



////  // Called when the form is displayed (before Submit is pressed)
//  def edit = Action {
//    implicit request =>
//      val form = Cache.getAs[Map[String,String]](cacheKey) match {
//        case Some(s) => assembly1SettingsForm.bind(s)
//        case None => assembly1SettingsForm.bind(flash.data).fill(Assembly1Settings.defaultSettings)
//      }
//      Ok(views.html.assembly1(form, ""))
//  }


  // Called when the form is displayed (before Submit is pressed)
  def edit = Action.async {
    implicit request =>
      Cache.getAs[Map[String,String]](cacheKey) match {
        case Some(s) =>
          val form = assembly1SettingsForm.bind(s)
          Future.successful(Ok(views.html.assembly1(form, "")))
        case None =>
          val json = Json.parse(defaultSettings.getConfig.toJson)
          // get the current values
          WS.url(getUrl).post(json).map {
            response =>
              val opt = settingsFromResponse(response)
              if (!opt.isEmpty) {
                val form = assembly1SettingsForm.fill(opt.get)
                Ok(views.html.assembly1(form, ""))
              } else {
                val form = assembly1SettingsForm.fill(Assembly1Settings.defaultSettings)
                Ok(views.html.assembly1(form, ""))
              }
          }
      }
  }

    // Called for the submit form button
  def save = Action.async {
    implicit request =>
      val form = if (flash.get("error").isDefined)
        assembly1SettingsForm.bind(flash.data)
      else assembly1SettingsForm.bindFromRequest()

      // Using cache since form data gets lost on redirect (XXX Is there a better way?)
      Cache.set(cacheKey, form.data)

      form.value.map {
        settings =>
          val json = Json.parse(settings.getConfig.toJson)
          WS.url(submitUrl).post(json).map {
            response =>
              val runIdOpt = runIdFromResponse(response)
              if (!runIdOpt.isEmpty) {
                val runId = runIdOpt.get
                Redirect(routes.Assembly1.show(runId)).flashing(flashResponse(response, runIdOpt))
              } else {
                Redirect(routes.Assembly1.edit).flashing(flashResponse(response, runIdOpt))
              }
          }
      } getOrElse {
        // The form did not validate, redirect with flash error message
         Future.successful(Redirect(routes.Assembly1.edit).flashing(Flash(form.data) +
          ("error" -> Messages("validation.errors"))))
      }
  }

  // Show the form after submit with RunId from command server
  def show(runId: String) = Action {
    implicit request =>
      val form = Cache.getAs[Map[String,String]](cacheKey) match {
        case Some(s) => assembly1SettingsForm.bind(s)
        case None => assembly1SettingsForm.bind(flash.data)
      }
      Ok(views.html.assembly1(form, runId))
  }

  // Returns the runId from the command server response body where it is in json format
  private def runIdFromResponse(response: Response): Option[String] = {
    if (response.status == ACCEPTED) {
      Json.fromJson[SubmitResponse](Json.parse(response.body)) map {
        submitResponse: SubmitResponse =>
          Some(submitResponse.runId)
      } recoverTotal {
        jserror: JsError => None
      }
    } else {
      None
    }
  }

  // Returns the current settings from the command server response body where it is in json format
  private def settingsFromResponse(response: Response): Option[Assembly1Settings] = {
    if (response.status == OK) {
      val json = Json.parse(response.body)
      val jsResult = Assembly1Settings.fromJson(json)
      jsResult.recoverTotal {
        jserror: JsError =>
          println(s"Error: JS error: $jserror")
          None
      }
      jsResult.asOpt
    } else {
      None
    }
  }


  // Returns a key value pair to put in the flash data for the request
  private def flashResponse(response: Response, runIdOpt: Option[String]): (String, String) = {
    if (response.status == ACCEPTED) {
      runIdOpt match {
        case Some(runId) => ("success", Messages("queue.submit.success") + s": RunId = $runId")
        case None => ("error", Messages("queue.submit.badResponse"))
      }
    } else {
      ("error", Messages("queue.submit.error") + s": ${response.statusText}: ${response.body}")
    }
  }


  // Websocket action, used to push the command status to the browser
  def status(runId: String) = WebSocket.using[String] { implicit request =>
    val statusChecker = StatusChecker(runId)
    val in = Iteratee.ignore[String]
    val out = Enumerator.generateM {
      statusChecker.getStatus(runId)
    }
    (in, out)
  }


  // Used to get the status of a submitted command from the command service Spray/REST HTTP server
  // using long polling and push it to the web page using a websocket.
  // Note: This could potentially be done directly from the Spray code. Not sure is Spray supports that
  // currently or if there would be problems with access, etc.
  private case class StatusChecker(runId: String) {
    var done = false

    // Returns true if the status means that the command is done
    private def statusIsFinal(status: String) : Boolean = {
      status == "completed" || status == "error" || status == "aborted" || status == "canceled"
    }

    // Returns the command status from the command server JSON response, or None on error
    private def commandStatusFromResponse(response: Response, runId: String): Option[String] = {
      if (!done && response.status == OK) {
        Json.fromJson[StatusResponse](Json.parse(response.body)) map {
          statusResponse: StatusResponse =>
              done = statusIsFinal(statusResponse.name)
              Some(statusResponse.name)
        } recoverTotal {
          jserror: JsError =>
            println(s"Error: JS error: $jserror")
            None
        }
      } else {
        None
      }
    }

    // Returns (eventually, maybe) the status of the command for the given runId
    def getStatus(runId: String): Future[Option[String]] = {
      WS.url(statusUrl(runId)).get().map {
        response =>
          commandStatusFromResponse(response, runId)
      }
    }
  }
}
