package csw.examples.vslice.seq

import akka.actor.ActorRef
import akka.util.Timeout
import csw.services.ccs.AssemblyController.Submit
import csw.services.events.EventService
import csw.util.config.Configurations
import csw.util.config.Configurations.{ConfigKey, SetupConfig}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * TMT Source Code: 12/4/16.
  */
object Demo {
  implicit val timeout = Timeout(10.seconds)

  val taName = "lgsTrombone"
  val thName = "lgsTromboneHCD"

  val componentPrefix: String = "nfiraos.ncc.trombone"

  // Public command configurations
  // Init submit command
  val initPrefix = s"$componentPrefix.init"
  val initCK: ConfigKey = initPrefix

  // Dataum submit command
  val datumPrefix = s"$componentPrefix.datum"
  val datumCK: ConfigKey = datumPrefix


  def getEventService(name: String = EventService.defaultName):EventService = {
    Await.result(EventService(name), timeout.duration)
  }

  def sendInit(ta: ActorRef):Unit = {
    val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))

    ta ! Submit(sca)


  }

}
