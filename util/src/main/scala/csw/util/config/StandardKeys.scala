package csw.util.config

import UnitsOfMeasure._

/**
 * Defines some standard keys for use in configurations
 */
object StandardKeys {

  sealed abstract class ExposureClass(val description: String) extends Serializable {
    override def toString = description
  }

  case object SCIENCE extends ExposureClass("science")

  case object NIGHTTIME_CALIBRATION extends ExposureClass("nighttime-calibration")

  case object DAYTIME_CALIBRATION extends ExposureClass("daytime-calibration")

  case object ACQUISITION extends ExposureClass("acquisition")



  sealed abstract class ExposureType(val description: String) extends Serializable {
    override def toString = description
  }

  case object FLAT extends ExposureType("flat")

  case object ARC extends ExposureType("arc")

  case object BIAS extends ExposureType("bias")

  case object OBSERVE extends ExposureType("observe")



  sealed abstract class CloudCoverType(val description: String, val percentage: Int) extends Serializable {
    override def toString = description
  }

  case object PERCENT_20 extends CloudCoverType("20%", 20)

  case object PERCENT_50 extends CloudCoverType("50%/Clear", 50)

  case object PERCENT_70 extends CloudCoverType("70%/Cirrus", 70)

  case object PERCENT_80 extends CloudCoverType("80%/Cloudy", 80)

  case object PERCENT_90 extends CloudCoverType("90%", 90)

  case object ANY extends CloudCoverType("Any", 100)


  // --- Common keys ---

  // --- Setup keys ---

  val position = StringKey("position", NoUnits)

  case object cloudCover extends Key1[CloudCoverType]("cloudCover", NoUnits) {
    def set(v: CloudCoverType) = CItem(this, v)
  }

    //  --- ObserveConfig ---

  val exposureTime = DoubleKey("exposureTime", NoUnits)

  case object exposureType extends Key1[ExposureType]("exposureType", NoUnits) {
    def set(v: ExposureType) = CItem(this, v)
  }

  case object exposureClass extends Key1[ExposureClass]("exposureClass", NoUnits) {
    def set(v: ExposureClass) = CItem(this, v)
  }

  val repeats = IntKey("repeats", NoUnits)

  // TODO: Add enum values
  val filter = StringKey("filter", NoUnits)
  val filterPrefix = "tcs.mobie.blue.filter"

  val disperser = StringKey("disperser", NoUnits)
  val disperserPrefix = "tcs.mobie.blue.disperser"
}
