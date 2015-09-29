package csw.util.cfg_old

import org.scalatest.FunSuite
import csw.util.cfg_old.Configurations._
import ConfigValues.ValueData._
import spray.json._
import csw.util.cfg_old.UnitsOfMeasure.{ NoUnits, Meters, Deg }
import scala.language.postfixOps

class ConfigJsonFormatsTests extends FunSuite with ConfigJsonFormats {

  test("Test converting a SetupConfig to JSON and back again") {
    val sc = SetupConfig(
      obsId = "2014-C2-4-44",
      "tcs.base.pos",
      "name" -> "m99",
      "full.name" -> "xxx m99",
      "ra" -> (10.0 deg),
      "dec" -> 2.0.deg,
      "equinox" -> "J2000",
      "nameList" -> List("xxx", "yyy", "zzz"),
      "nameTuple" -> ("aaa", "bbb", "ccc"),
      //      "intTuple" -> (1, 2, 3).deg, // XXX Tuple looses type info
      "intList" -> List(1, 2, 3).deg,
      "intVal" -> 22.meters,
      "doubleVal" -> 3.14).withValues("added1" -> 1, "added2" -> 2.deg)

    val json = sc.toJson
    val s = json.prettyPrint
    println(s)

    val js = s.parseJson
    assert(json == js)

    val config = js.convertTo[SetupConfig]
    val json2 = config.toJson
    assert(json == json2)

    val s2 = json2.prettyPrint

    assert(s == s2)

    // direct compare doesn't work, due to differences in collection type for names (List != ::)
    //    println(s"config = $config")
    //    assert(config == sc)

    assert(config.prefix == "tcs.base.pos")
    assert(config.obsId == "2014-C2-4-44")
    assert(config("name").elems.head == "m99")
    assert(config("full.name").elems.head == "xxx m99")
    assert(config("ra") == 10.0.deg)
    assert(config.get("dec").get.elems.head == 2.0)
    assert(config.get("xxx") == None)
    assert(config("dec").units == Deg)
    assert(config("nameList").elems == List("xxx", "yyy", "zzz"))
    assert(config("nameTuple").elems == List("aaa", "bbb", "ccc"))
    assert(config("intList").elems == List(1, 2, 3))
    assert(config("intList").units == Deg)
    assert(config("intVal").elems.head == 22)
    assert(config("intVal").units == Meters)
    assert(config("doubleVal").elems.head == 3.14)
    assert(config("doubleVal").units == NoUnits)
    assert(config.exists("added1"))
    assert(config("added2") == 2.deg)

  }

  test("Test converting a mixed ConfigList to JSON and back again") {
    val obsId = "TMT-2021A-C-2-1"
    val testConfig: List[ConfigType] = List(
      SetupConfig(
        obsId = obsId,
        "tmt.tel.base.pos",
        "posName" -> "NGC738B",
        "c1" -> "22:35:58.530",
        "c2" -> "33:57:55.40",
        "equinox" -> "J2000"),
      WaitConfig(obsId),
      ObserveConfig(obsId),
      SetupConfig(
        obsId = obsId,
        "tmt.tel.ao.pos.one",
        "c1" -> "22:356:01.066",
        "c2" -> "33:58:21.69",
        "equinox" -> "J2000"),
      WaitConfig(obsId),
      ObserveConfig(obsId))

    val json = testConfig.toJson
    val s = json.prettyPrint
    println(s)

    val js = s.parseJson
    assert(json == js)

    val configList = js.convertTo[List[ConfigType]]
    val json2 = configList.toJson
    assert(json == json2)

    val s2 = json2.prettyPrint
    //    println(s2)

    assert(s == s2)
  }
}

