package csw.util.cfg

import org.scalatest.FunSuite
import akka.actor.ActorSystem
import akka.serialization._
import csw.util.cfg.Configurations.SetupConfig
import ConfigValues.ValueData._

/**
 * Test using Akka serialization with ConfigValue (needed for kvm, redis projects)
 */
class ConfigValuesSerTests extends FunSuite {

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

    val system = ActorSystem("example")
    val serialization = SerializationExtension(system)
    val serializer = serialization.findSerializerFor(sc)
    val bytes = serializer.toBinary(sc)
    val sc2 = serializer.fromBinary(bytes, manifest = None)
    assert(sc2 == sc)
  }
}
