package csw.examples.vslice.assembly

import csw.examples.vslice.assembly.AssemblyContext.{TromboneCalculationConfig, TromboneControlConfig}
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.{ComponentId, ComponentType, Connection}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.pkg.Component.{AssemblyInfo, DoNotRegister, RegisterAndTrackServices}

/**
 * TMT Source Code: 8/12/16.
 */
object AssemblyTestData {

  import Algorithms._

  val testFocusErrors = -20.0 to 20.0 by 4.0
  val testZenithAngles = 0.0 to 60.0 by 5.0

  val hcdId = ComponentId("lgsTromboneHCD", ComponentType.HCD)

  val TestAssemblyInfo = AssemblyInfo(
    "lgsTrombone",
    "nfiraos.ncc.trombone",
    "csw.examples.vslice.assembly.TromboneAssembly",
    RegisterAndTrackServices, Set(AkkaType), Set(AkkaConnection(hcdId))
  )

  val TestCalculationConfig = TromboneCalculationConfig(
    defaultInitialElevation = 95.0,
    focusErrorGain = .75, upperFocusLimit = 20.0, lowerFocusLimit = -20.0,
    zenithFactor = 4
  )

  val TestControlConfig = TromboneControlConfig(
    positionScale = 8.0,
    stageZero = 90.0, minStageEncoder = 225, minEncoderLimit = 200, maxEncoderLimit = 1200
  )

  val TestAssemblyContext = AssemblyContext(TestAssemblyInfo, TestCalculationConfig, TestControlConfig)

  val maxRDError = focusErrorToRangeError(TestCalculationConfig, TestCalculationConfig.upperFocusLimit)
  val maxRD = zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles.last)
  val maxTotalRD = maxRDError + zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles.last)

  val minRDError = focusErrorToRangeError(TestCalculationConfig, TestCalculationConfig.lowerFocusLimit)
  val minRD = zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles.head)
  val minTotalRD = minRDError + zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles.head)

  val minReasonableAltitude = 85.0
  val maxReasonableAltitude = 110.0
  val minReasonableRange = minReasonableAltitude
  val maxReasonableRange = 200.0
  val minReasonableStage = minReasonableAltitude
  val maxReasonableStage = 200.0
  val maxReasonableRangeError = 5.0
  val minReasonableRangeError = -5.0

  // These are values for testing the range error based on focus error (fe, fe distance error)
  val feRangeErrorTestValues = testFocusErrors.map(f => (f, focusErrorToRangeError(TestCalculationConfig, f)))

  // This provides "base" range distance based on za only (za, rd)
  val zaRangeDistance = testZenithAngles.map(f => (f, zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, f)))

  // This produces an Vector of data of form (newRangeDistance, newElevation), (newRangeDistance, newElevation),...) as a function of za at a given focus error
  def newRangeAndElData(fe: Double) = testZenithAngles.map(focusZenithAngleToElevationAndRangeDistance(TestCalculationConfig, TestCalculationConfig.defaultInitialElevation, fe, _))

  // This produces an Vector of data of form (newRangeDistance, newElevation), (newRangeDistance, newElevation),...) as a function of za at a given focus error
  def newZARangeAndElData(za: Double) = testFocusErrors.map(focusZenithAngleToElevationAndRangeDistance(TestCalculationConfig, TestCalculationConfig.defaultInitialElevation, _, za))

  type TestValue = ((Double, Double), Int)

  def getza(t: TestValue): Double = t._1._1

  def gettrd(t: TestValue): Double = t._1._2

  def getenc(t: TestValue): Int = t._2

  /**
   * This routine takes the two configs and returns a tuple of ((zenith angle, totalRangeDistance), encoder position)
   * for the range of zenith angle 0 to 60 in steps of 5 degrees
   *
   * @param calculationConfig the assembly calculation config provides algorithm parameters
   * @param controlConfig the control config provides values for mapping stage positions into encoder values
   * @return a Vector of the form (((za, totalRD), enc), ((za, totalRD), enc), ...)
   */
  def calculatedTestData(calculationConfig: TromboneCalculationConfig, controlConfig: TromboneControlConfig, fe: Double) = {

    val rde = focusErrorToRangeError(calculationConfig, fe)

    val rds = testZenithAngles.map(zenithAngleToRangeDistance(calculationConfig.defaultInitialElevation, _) + rde)

    val enc = rds.map(f => stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(f)))

    val tuples = testZenithAngles.zip(rds).zip(enc)
    tuples
  }

  /**
   * This routine takes the two configs and returns a tuple of ((zenith angle, totalRangeDistance), encoder position)
   * for the range of focus error -20 to 20 in steps of 2 um
   *
   * @param calculationConfig the assembly calculation config provides algorithm parameters
   * @param controlConfig the control config provides values for mapping stage positions into encoder values
   * @return a Vector of the form (((fe, totalRD), enc), ((fe, totalRD), enc), ...)
   */
  def calculatedFETestData(calculationConfig: TromboneCalculationConfig, controlConfig: TromboneControlConfig, elevation: Double, za: Double) = {

    val rangeDistance1 = zenithAngleToRangeDistance(elevation, za)
    val rangeError = testFocusErrors.map(focusErrorToRangeError(calculationConfig, _))
    val totalRangeDistance = rangeError.map(f => f + rangeDistance1)

    val enc = totalRangeDistance.map(f => stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(f)))

    val tuples = testFocusErrors.zip(totalRangeDistance).zip(enc)
    tuples
  }

  val minStagePos = rangeDistanceToStagePosition(minTotalRD)
  val maxStagePos = rangeDistanceToStagePosition(maxTotalRD)

  // These values take a total el + range to encoder value
  val encoderTestValues: Vector[(Double, Int)] = Vector(
    (80.0, 200),
    (90.0, 225),
    (100.0, 305),
    (110.0, 385),
    (120.0, 465),
    (130.0, 545),
    (140.0, 625),
    (150.0, 705),
    (160.0, 785),
    (170.0, 865),
    (180.0, 945),
    (190.0, 1025)
  )

}
