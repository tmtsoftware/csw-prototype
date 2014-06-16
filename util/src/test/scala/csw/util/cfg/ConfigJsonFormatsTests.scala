package csw.util.cfg

import org.scalatest.FunSuite
import Configurations.SetupConfig
import ConfigValues.ValueData._
import spray.json._

//import DefaultJsonProtocol._

class ConfigJsonFormatsTests extends FunSuite with ConfigJsonFormats {

  test("Test converting a SetupConfig to JSON and back again") {
    val sc = SetupConfig(
      obsId = "2014-C2-4-44",
      "tcs.base.pos",
      "name" -> "m99",
      "ra" -> (10.0 deg),
      "dec" -> 2.0.deg,
      "equinox" -> "J2000",
      "nameList" -> List("xxx", "yyy", "zzz"),
      "nameTuple" ->("aaa", "bbb", "ccc"),
      //      "intTuple" -> (1, 2, 3).deg, // XXX Tuple looses type info
      "intList" -> List(1, 2, 3).deg,
      "intVal" -> 22,
      "doubleVal" -> 3.14
    )

    //    println(s"sc = $sc")

    val json = sc.toJson
    val s = json.prettyPrint
    println(s)

    val js = s.parseJson
    assert(json == js)

    val config = js.convertTo[SetupConfig]
    val json2 = config.toJson
    assert(json == json2)

    val s2 = json2.prettyPrint
    println(s2)

    // XXX Ints are turned into Doubles...
    assert(s == s2)

    // direct compare doesn't work, due to differences in collection type for names (List != ::)
    //    println(s"config = $config")
    //    assert(config == sc)
  }
}

