package csw.services.ccs

import com.typesafe.scalalogging.LazyLogging
import csw.services.ccs.CommandStatus._
import csw.services.ccs.Validation.{OtherIssue, ValidationIssue, WrongInternalStateIssue}
import csw.util.param.Parameters.{CommandInfo, SequenceCommand, Setup}
import csw.util.param._
import org.scalatest.FunSpec
import spray.json._

/**
 * TMT Source Code: 12/18/16.
 */
class CommandsJSONTests extends FunSpec with LazyLogging {
  import CommandsJSON._

  val itemSetInfo = CommandInfo(ObsId("001"))

  describe("Overall Tests") {
    it("should work with validation issues") {

      val vi: ValidationIssue = Validation.SingleConfigOnlyIssue("testing 1, 2, 3")

      logger.info(s"Its: $vi")

      val json = vi.toJson

      logger.info(s"json: $json")

      val v: ValidationIssue = json.convertTo[ValidationIssue]

    }

    it("should work with command status") {
      var csIn: CommandResponse = CommandStatus.Invalid(OtherIssue("No Good reasons"))
      var json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      var csOut = json.convertTo[CommandResponse]
      assert(csIn == csOut)

      csIn = NoLongerValid(WrongInternalStateIssue("wrong state!"))
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandResponse]
      assert(csIn.equals(csOut))

      csIn = Completed
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandResponse]
      assert(csIn.equals(csOut))

      csIn = InProgress("I'm in progress!")
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandResponse]
      assert(csIn.equals(csOut))

      csIn = Error("I'm an error!")
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandResponse]
      assert(csIn.equals(csOut))

      val k1 = StringKey("test")
      val i1 = k1.set("testv1", "testv2").withUnits(UnitsOfMeasure.degrees)
      val k2 = DoubleKey("MyDouble")
      val i2 = k2.set(1000.34)
      val sc = Setup(itemSetInfo, "wfos.blue.det").madd(i1, i2)

      csIn = Aborted
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandResponse]
      assert(csIn.equals(csOut))

      csIn = Cancelled
      json = csIn.toJson
      //logger.info(s"json: ${json.prettyPrint}")
      csOut = json.convertTo[CommandResponse]
      assert(csIn.equals(csOut))
    }

    it("should work with configs") {
      val k1 = StringKey("test")
      val i1 = k1.set("testv1", "testv2").withUnits(UnitsOfMeasure.degrees)
      val k2 = DoubleKey("MyDouble")
      val i2 = k2.set(1000.34)
      val scIn: SequenceCommand = Setup(itemSetInfo, "wfos.blue.det").madd(i1, i2)

      val json = scIn.toJson
      logger.info(s"json: ${json.prettyPrint}")
      val scOut = json.convertTo[SequenceCommand]
      logger.info(s"cs: $scOut")
      assert(scIn.equals(scOut))
    }

  }

}
