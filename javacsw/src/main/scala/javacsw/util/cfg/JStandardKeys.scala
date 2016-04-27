package javacsw.util.cfg

import csw.util.cfg.StandardKeys
import csw.util.cfg.StandardKeys._

/**
 * Java API for standard keys
 */
object JStandardKeys {
  val SCIENCE: ExposureClass = StandardKeys.SCIENCE
  val NIGHTTIME_CALIBRATION: ExposureClass = StandardKeys.NIGHTTIME_CALIBRATION
  val DAYTIME_CALIBRATION: ExposureClass = StandardKeys.DAYTIME_CALIBRATION
  val ACQUISITION: ExposureClass = StandardKeys.ACQUISITION

  val FLAT: ExposureType = StandardKeys.FLAT
  val ARC: ExposureType = StandardKeys.ARC
  val BIAS: ExposureType = StandardKeys.BIAS
  val OBSERVE: ExposureType = StandardKeys.OBSERVE

  val PERCENT_20: CloudCoverType = StandardKeys.PERCENT_20
  val PERCENT_50: CloudCoverType = StandardKeys.PERCENT_50
  val PERCENT_70: CloudCoverType = StandardKeys.PERCENT_70
  val PERCENT_80: CloudCoverType = StandardKeys.PERCENT_80
  val PERCENT_90: CloudCoverType = StandardKeys.PERCENT_90
  val ANY: CloudCoverType = StandardKeys.ANY
}
