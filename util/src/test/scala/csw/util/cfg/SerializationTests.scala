package csw.util.cfg

import csw.util.cfg.Configurations._
import csw.util.cfg.Events.{StatusEventType, Event, StatusEvent}
import org.scalatest.FunSuite

/**
 * Created by gillies on 7/30/15.
 */
class SerializationTests extends FunSuite {
  import StandardKeys._

  val obsId = ObsId("2023-Q22-4-33")

  val fqn1 = "tcs.base.pos.name"
  val fqn1prefix = "tcs.base.pos"
  val fqn1name = "name"
  val fqn2 = "tcs.base.pos.ra"
  val fqn3 = "tcs.base.pos.dec"

  val ra = Key.create[String]("ra")
  val dec = Key.create[String]("dec")
  val epoch = Key.create[Double]("epoch")
  val test = Key.create[Int]("test")

  val sc1 = SetupConfig("tcs.pos")
    .set(ra, "12:32:11")
    .set(dec, "30:22:22")
    .set(epoch, 1950)
    .set(test, 1) //.second

  val disperser = Key.create[String]("disperser")
  val filter1 = Key.create[String]("filter1")
  val sc2 = SetupConfig("wfos.blue")
    .set(disperser, "gr243")
    .set(filter1, "GG433")

  val ob1 = ObserveConfig("wfos.blue.camera")
    .set(exposureTime, 22.3) // .sec,
    .set(repeats, 3)

  val wc1 = WaitConfig("wfos.blue.camera")

  test("ConfigType Java serialization") {
    import ConfigSerializer._

    // ("Test setup config Java serialization") {
    val bytes = write(sc1)
    val scout = read[SetupConfig](bytes)
    assert(scout == sc1)

    //("Test observe config Java serialization") {
    val bytes1 = write(ob1)
    val obout = read[ObserveConfig](bytes1)
    assert(obout == ob1)

    //("Test wait config Java serialization") {
    val bytes2 = write(wc1)
    val wout = read[WaitConfig](bytes2)
    assert(wout == wc1)
  }

  test("SetupConfigArg Java serialization") {
    import ConfigSerializer._

    val sca1 = SetupConfigArg(ConfigInfo(obsId), Seq(sc1))
    val bytes1 = write(sca1)

    val sout1 = read[SetupConfigArg](bytes1)
    assert(sout1 == sca1)
  }

  test("ObserveConfigArg Java serialization") {
    import ConfigSerializer._

    val oca1 = ObserveConfigArg(ConfigInfo(obsId), Seq(ob1))
    val bytes1 = write(oca1)

    val out1 = read[ObserveConfigArg](bytes1)
    assert(out1 == oca1)
  }

  test("WaitConfigArg Java serialization") {
    import ConfigSerializer._

    val wca1 = WaitConfigArg(ConfigInfo(obsId), wc1)
    val bytes1 = write(wca1)

    val out1 = read[WaitConfigArg](bytes1)
    assert(out1 == wca1)
  }

  test("Event built-in pickler serialization support") {
    val event = StatusEvent(fqn1prefix)
      .set(ra, "12:32:11")
      .set(dec, "30:22:22")

    val ar = event.toByteArray
    val eventCopy = Event(ar)
    assert(event.eventType == StatusEventType)
    assert(event == eventCopy.asInstanceOf[StatusEvent])
  }

}
