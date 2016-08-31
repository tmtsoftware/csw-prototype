package csw.util.config

import csw.util.config.Configurations.ConfigKey
import csw.util.config.Events.{EventInfo, EventTime, SystemEvent}
import org.scalatest.{FunSpec, ShouldMatchers}

/**
  * TMT Source Code: 8/17/16.
  */
class EventTests extends FunSpec with ShouldMatchers {
  private val s1: String = "encoder"
  private val s2: String = "filter"
  private val s3: String = "detectorTemp"

  private val s1Key = IntKey(s1)
  private val s2Key = StringKey(s2)

  private val ck = "wfos.blue.filter"
  private val ck1 = "wfos.prog.cloudcover"
  private val ck2 = "wfos.red.filter"
  private val ck3 = "wfos.red.detector"

  describe("Check equal for EventInfo") {
    val ck1:ConfigKey = ck
    val testtime = EventTime()

    it("should be equals since case class") {

      val ei1 = EventInfo(ck1, testtime, None)

      val ei2 = EventInfo(ck1, testtime, None)

      ei1 should equal(ei2)
    }

    it("should work with obsid too") {
      val obsID1 = ObsId("2022A-Q-P123-O456-F7890")
      val obsID2 = ObsId("2022A-Q-P123-O456-F7891")

      val ei1 = EventInfo(ck1, testtime, Some(obsID1))

      val ei2 = EventInfo(ck1, testtime, Some(obsID1))

      ei1 should equal(ei2)
    }
  }

  describe("Check equal for events") {
    it("should have equals working on SystemEvents") {

      val ev1 = SystemEvent(ck).add(s1Key -> 2)

      val ev2 = SystemEvent(ck).add(s1Key -> 2)

      val ev3 = SystemEvent(ck).add(s1Key -> 22)

      ev1.info should equal(ev2.info)

      ev1 should equal(ev2)
      ev1 should not equal(ev3)
    }
  }

}
