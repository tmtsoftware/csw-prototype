import akka.actor.ActorRef
import csw.services.event.EventSubscriber
import csw.util.config.Configurations.ConfigKey
import csw.util.config.Events.{StatusEvent, SystemEvent}
import csw.util.config.{FloatItem, FloatKey, UnitsOfMeasure}
import csw.util.config.Subsystem.{RTC, TCS}
import csw.util.config.UnitsOfMeasure.Units

/**
  * TMT Source Code: 6/20/16.
  */
class TromboneEventSubscriber(name: String, calculationActor: ActorRef) extends EventSubscriber {
  import TromboneEventSubscriber._

  // If state of NSS is false, then subscriber provides 0 for zenith distance with updates to subscribers
  var stateOfNSS = false
  var zenithAngle:Option[FloatItem] = Some(zenithAngleKey.set(0.0f).withUnits(UnitsOfMeasure.Deg))
  var focusError:FloatItem = focusKey.set(0.0f).withUnits(UnitsOfMeasure.Meters)

  def receive: Receive = {

    case event: SystemEvent =>
      val src = event.info.source
      src match {
        case `zConfigKey`=>
          // Update for zenith distance
          //zenithAngle = event.get(zenithAngleKey).asInstanceOf[Option[FloatItem]]
        case `focusConfigKey` =>
          // Update for focus Config Key
      }
    case UsingNSS(inUse) =>
      stateOfNSS = inUse
    case _ => println("some message")

  }

  def update() = {
    //calculationActor !
  }

}

object TromboneEventSubscriber {

  // ConfigKeys for incoming event matching
  // This is the zenith angle from TCS
  val zConfigKey = ConfigKey(TCS, "tcsPk.zenithAngle")
  // This is the focus error from RTC
  val focusConfigKey = ConfigKey(RTC, "rtc.focusError")

  // Key values
  val focusKey = FloatKey("focus")
  val zenithAngleKey = FloatKey("zenithAngle")


  // Messages received by the TromboneSubscriber
  case class UsingNSS(inUse: Boolean)
}


