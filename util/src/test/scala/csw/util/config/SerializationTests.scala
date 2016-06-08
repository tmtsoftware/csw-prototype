package csw.util.config

import csw.util.config.Configurations._
import csw.util.config.Events.{EventServiceEvent, SystemEvent}
import csw.util.config.StateVariable._
import org.scalatest.FunSuite

class SerializationTests extends FunSuite {

  val obsId = ObsId("2023-Q22-4-33")

  val fqn1 = "tcs.base.pos.name"
  val fqn1prefix = "tcs.base.pos"
  val fqn1name = "name"
  val fqn2 = "tcs.base.pos.ra"
  val fqn3 = "tcs.base.pos.dec"

  val exposureTime = DoubleKey("exposureTime")
  val repeats = IntKey("repeats")
  val ra = StringKey("ra")
  val dec = StringKey("dec")
  val epoch = DoubleKey("epoch")
  val test = IntKey("test")

  val sc1 = SetupConfig("tcs.pos")
    .set(ra, "12:32:11")
    .set(dec, "30:22:22")
    .set(epoch, 1950.0)
    .set(test, 1) //.second

  val cs1 = CurrentState("tcs.pos")
    .set(ra, "12:32:11")
    .set(dec, "30:22:22")
    .set(epoch, 1950.0)
    .set(test, 1) //.second

  val disperser = StringKey("disperser")
  val filter1 = StringKey("filter1")
  val sc2 = SetupConfig("wfos.blue")
    .set(disperser, "gr243")
    .set(filter1, "GG433")

  val ob1 = ObserveConfig("wfos.blue.camera")
    .set(exposureTime, 22.3) // .sec,
    .set(repeats, 3)

  val wc1 = WaitConfig("wfos.blue.camera")

  test("ConfigType Java serialization") {
    import ConfigSerializer._

    // Test setup config Java serialization
    val bytes = write(sc1)
    val scout = read[SetupConfig](bytes)
    assert(scout == sc1)

    // Test observe config Java serialization
    val bytes1 = write(ob1)
    val obout = read[ObserveConfig](bytes1)
    assert(obout == ob1)

    // Test wait config Java serialization
    val bytes2 = write(wc1)
    val wout = read[WaitConfig](bytes2)
    assert(wout == wc1)

    // Test current state Java serialization
    val bytes3 = write(cs1)
    val csout = read[CurrentState](bytes3)
    assert(csout == cs1)
  }

  test("SetupConfigArg Java serialization") {
    import ConfigSerializer._

    val sca1 = SetupConfigArg(ConfigInfo(obsId), sc1)
    val bytes1 = write(sca1)

    val sout1 = read[SetupConfigArg](bytes1)
    assert(sout1 == sca1)
  }

  test("ObserveConfigArg Java serialization") {
    import ConfigSerializer._

    val oca1 = ObserveConfigArg(ConfigInfo(obsId), ob1)
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

  test("Base trait config Java serialization") {
    import ConfigSerializer._

    val wca1 = WaitConfigArg(ConfigInfo(obsId), wc1)
    val bytes1 = write(wca1)

    val out1 = read[SequenceConfigArg](bytes1)
    assert(out1 == wca1)
  }

  test("Base trait event Java serialization") {
    import ConfigSerializer._
    val event = SystemEvent(fqn1prefix)
      .set(ra, "12:32:11")
      .set(dec, "30:22:22")

    val bytes1 = write(event)

    val out1 = read[EventServiceEvent](bytes1)
    assert(out1 == event)
  }

  test("CurrentStates Java serialization") {
    import ConfigSerializer._

    val sca1 = CurrentStates(List(cs1))
    val bytes1 = write(sca1)

    val sout1 = read[CurrentStates](bytes1)
    assert(sout1 == sca1)
  }

}
