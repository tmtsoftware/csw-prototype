package csw.util.cfg

import csw.util.cfg.UnitsOfMeasure.Units

object StandardKeys {

  protected[StandardKeys] sealed abstract class ExposureClass(val description: String) extends Serializable {
    override def toString = description
  }

  case object SCIENCE extends ExposureClass("science")

  case object NIGHTTIME_CALIBRATION extends ExposureClass("nighttime-calibration")

  case object DAYTIME_CALIBRATION extends ExposureClass("daytime-calibration")

  case object ACQUISITION extends ExposureClass("acquisition")

  protected[StandardKeys] sealed abstract class ExposureType(val description: String) extends Serializable {
    override def toString = description
  }

  case object FLAT extends ExposureType("flat")

  case object ARC extends ExposureType("arc")

  case object BIAS extends ExposureType("bias")

  case object OBSERVE extends ExposureType("observe")

  protected[StandardKeys] sealed abstract class CloudCoverType(val description: String, val percentage: Int) extends Serializable {
    override def toString = description
  }

  case object PERCENT_20 extends CloudCoverType("20%", 20)

  case object PERCENT_50 extends CloudCoverType("50%/Clear", 50)

  case object PERCENT_70 extends CloudCoverType("70%/Cirrus", 70)

  case object PERCENT_80 extends CloudCoverType("80%/Cloudy", 80)

  case object PERCENT_90 extends CloudCoverType("90%", 90)

  case object ANY extends CloudCoverType("Any", 100)

  // Common keys
  val units = Key.create[Units]("units")

  // Setup keys
  val position = Key.create[String]("position")
  val cloudCover = Key.create[CloudCoverType]("cloudCover")

  // ObserveConfig
  val exposureTime = Key.create[Double]("exposureTime")
  val exposureType = Key.create[ExposureType]("exposureType")
  val exposureClass = Key.create[ExposureClass]("exposureClass")
  val repeats = Key.create[Int]("repeats")
}
