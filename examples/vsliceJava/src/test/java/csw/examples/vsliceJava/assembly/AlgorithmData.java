package csw.examples.vsliceJava.assembly;

///**
//  * TMT Source Code: 8/12/16.
//  */
//object AlgorithmData {
//
//  val TestCalculationConfig = CalculationConfig(defaultInitialElevation = 90.0,
//    focusErrorGain = 1.0, upperFocusLimit = 20.0, lowerFocusLimit = -20.0,
//    zenithFactor = 4.0)
//
//  val TestControlConfig = TromboneControlConfig(positionScale = 60.0, minElevation = 85.0, minElevationEncoder = 180)
//
//  // These are values for testing the range function
//  val rangeTestValues: Vector[(Double, Double)] = Vector(
//    (-20.0, -5.00),
//    (-15.0, -3.75),
//    (-10.0, -2.50),
//    (-05.0, -1.25),
//    (0.0, 0.00),
//    (5.0, 1.25),
//    (10.0, 2.50),
//    (15.0, 3.75),
//    (20.0, 5.00)
//  )
//
//  // These are test values for testing the NA layer elevation function
//  val elevationTestValues: Vector[(Double, Double)] = Vector(
//    (0.0, 94.00),
//    (5.0, 93.98) /*,
//    (10.0, 93.94),
//    (15.0, 93.86),
//    (20.0, 93.76),
//    (25.0, 93.62),
//    (30.0, 93.46),
//    (35.0, 93.28),
//    (40.0, 93.06),
//    (45.0, 92.83),
//    (50.0, 92.57),
//    (55.0, 92.29),
//    (60.0, 92.00),
//    (65.0, 91.69),
//    (70.0, 91.36) */
//  )
//
//
//  // These values take a total el + range to encoder value
//  val encoderTestValues: Vector[(Double, Int)] = Vector(
//    (86.37, 262),
//    (88.0, 360),
//    (90.0, 480),
//    (92.0, 600),
//    (94.0, 720),
//    (96.0, 840),
//    (98.0, 960),
//    (99.0, 1020)
//  )
//}
