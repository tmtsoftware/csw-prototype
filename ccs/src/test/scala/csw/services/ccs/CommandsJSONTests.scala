package csw.services.ccs

import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.ccs.CommandStatus._
import csw.services.ccs.Validation.{OtherIssue, ValidationIssue, WrongInternalStateIssue}
import csw.util.config.Configurations.{ObserveConfig, SequenceConfig, SetupConfig}
import csw.util.config.{DoubleKey, RunId, StringKey, UnitsOfMeasure}
import org.scalatest.FunSpec
import spray.json._

/**
 * TMT Source Code: 12/18/16.
 */
class CommandsJSONTests extends FunSpec with LazyLogging {
  import CommandsJSON._

  describe("Overall Tests") {
    it("should work with validation issues") {

      val vi: ValidationIssue = Validation.SingleConfigOnlyIssue("testing 1, 2, 3")

      logger.info(s"Its: $vi")

      val json = vi.toJson

      logger.info(s"json: $json")

      val v: ValidationIssue = json.convertTo[ValidationIssue]

    }

    it("should work with command status") {
      var csIn: CommandStatus = CommandStatus.Invalid(OtherIssue("No Good reasons"))
      var json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      var csOut = json.convertTo[CommandStatus]
      assert(csIn == csOut)

      csIn = Valid
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandStatus]
      assert(csIn.equals(csOut))

      csIn = NoLongerValid(WrongInternalStateIssue("wrong state!"))
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandStatus]
      assert(csIn.equals(csOut))

      csIn = Completed
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandStatus]
      assert(csIn.equals(csOut))

      csIn = InProgress("I'm in progress!")
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandStatus]
      assert(csIn.equals(csOut))

      csIn = Error("I'm an error!")
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandStatus]
      assert(csIn.equals(csOut))

      val k1 = StringKey("test")
      val i1 = k1.set("testv1", "testv2").withUnits(UnitsOfMeasure.degrees)
      val k2 = DoubleKey("MyDouble")
      val i2 = k2.set(1000.34)
      val sc = SetupConfig("wfos.blue.det").madd(i1, i2)

      csIn = CompletedWithResult(sc)
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandStatus]
      logger.info(s"cs: $csOut")
      assert(csIn.equals(csOut))

      csIn = Aborted
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandStatus]
      assert(csIn.equals(csOut))

      csIn = Cancelled
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandStatus]
      assert(csIn.equals(csOut))
    }

    it("should work with configs") {
      val k1 = StringKey("test")
      val i1 = k1.set("testv1", "testv2").withUnits(UnitsOfMeasure.degrees)
      val k2 = DoubleKey("MyDouble")
      val i2 = k2.set(1000.34)
      val scIn: SequenceConfig = SetupConfig("wfos.blue.det").madd(i1, i2)

      val json = scIn.toJson
      logger.info(s"json: ${json.prettyPrint}")
      val scOut = json.convertTo[SequenceConfig]
      logger.info(s"cs: $scOut")
      assert(scIn.equals(scOut))
    }

    it("should work with CommandResultPair") {
      val k1 = StringKey("test")
      val i1 = k1.set("testv1", "testv2").withUnits(UnitsOfMeasure.degrees)
      val k2 = DoubleKey("MyDouble")
      val i2 = k2.set(1000.34)
      val sc: SequenceConfig = SetupConfig("wfos.blue.det").madd(i1, i2)

      val crIn = CommandResultPair(CommandStatus.Completed, sc)

      val json = crIn.toJson
      logger.info(s"json: ${json.prettyPrint}")
      val crOut = json.convertTo[CommandResultPair]
      logger.info(s"cr: $crOut")
      assert(crIn.equals(crOut))
    }

    it("Should work with command result") {
      val r = RunId()
      val o = CommandStatus.Accepted
      val k1 = StringKey("test")
      val i1 = k1.set("testv1", "testv2").withUnits(UnitsOfMeasure.degrees)
      val k2 = DoubleKey("test2")
      val i2 = k2.set(1000.34)
      val cr = CommandResults(List(
        CommandResultPair(Completed, SetupConfig("wfos.blue").add(i1)),
        CommandResultPair(Invalid(OtherIssue("What the fuck")), ObserveConfig("wfos.blue.det").madd(i1, i2))
      ))
      val crIn: CommandResult = CommandResult(r, o, cr)

      val json: JsValue = crIn.toJson

      logger.info(s"json: ${json.prettyPrint}")

      val crOut = json.convertTo[CommandResult]
      logger.info(s"cr: $crOut")
      assert(crIn.equals(crOut))
    }

  }

}
