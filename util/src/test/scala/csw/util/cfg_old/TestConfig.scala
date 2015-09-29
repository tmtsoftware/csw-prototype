package csw.util.cfg_old

import csw.util.cfg_old.Configurations._

object TestConfig {
  val obsId = "TMT-2021A-C-2-1"

  // Config to u/se for testing
  val testConfig = List(
    SetupConfig(
      obsId = obsId,
      "tmt.tel.base.pos",
      "posName" -> "NGC738B",
      "c1" -> "22:35:58.530",
      "c2" -> "33:57:55.40",
      "equinox" -> "J2000"),
    SetupConfig(
      obsId = obsId,
      "tmt.tel.ao.pos.one",
      "c1" -> "22:356:01.066",
      "c2" -> "33:58:21.69",
      "equinox" -> "J2000"))

  // Reference config for use with checkValid: Tests only presence of keys and value types
  val refConfig = List(
    SetupConfig(
      obsId = obsId,
      "tmt.tel.base.pos",
      "posName" -> "",
      "c1" -> "",
      "c2" -> "",
      "equinox" -> ""),
    SetupConfig(
      obsId = obsId,
      "tmt.tel.ao.pos.one",
      "c1" -> "",
      "c2" -> "",
      "equinox" -> ""))
}

