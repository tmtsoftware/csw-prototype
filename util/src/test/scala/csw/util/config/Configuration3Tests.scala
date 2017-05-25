package csw.util.config

import csw.util.config.Configurations3._

import csw.util.config.UnitsOfMeasure.{degrees, meters, _}
import org.scalatest.FunSpec
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Vector
import scala.util.Try

/**
  * Created by gillies on 5/25/17.
  */
object Configuration3Tests {

  import DefaultJsonProtocol._

  case class MyData(i: Int, f: Float, d: Double, s: String)

  implicit val MyDataFormat = jsonFormat4(MyData.apply)
}

//noinspection ComparingUnrelatedTypes,ScalaUnusedSymbol
class Configuration3Tests extends FunSpec {

  import Configuration3Tests._

  private val ck = "wfos.blue.filter"
  private val ck1 = "wfos.prog.cloudcover"
  private val ck2 = "wfos.red.filter"
  private val ck3 = "wfos.red.detector"

  val k1 = IntKey("itest")
  val k2 = DoubleKey("dtest")
  val k3 = StringKey("stest")
  val k4 = LongArrayKey("lartest")

  val i1 = k1.set(1, 2, 3).withUnits(UnitsOfMeasure.degrees)
  val i11 = k1.set(1, 2, 3).withUnits(UnitsOfMeasure.degrees) // This is here to see if it is checking equality or address
  val i2 = k2.set(1.0, 2.0, 3.0).withUnits(UnitsOfMeasure.meters)
  val i3 = k3.set("A", "B", "C")
  val i4 = k4.set(LongArray(Array.fill[Long](100)(10)), LongArray(Array.fill[Long](100)(100)))
  val i5 = k1.set(22) // This is not added for testing not present removal

  describe("test Configurations3 Setup") {
    val encoder1 = IntKey("encoder1")
    val encoder2 = IntKey("encoder2")
    val exp = FloatKey("exposureTime")
    val repeats = IntKey("repeats")
    val seconds = IntKey("seconds")

    val obsId = "Obs001"

    val sc1 = Setup(obsId, ck1).madd(encoder1.set(22), encoder2.set(33).withUnits(UnitsOfMeasure.degrees))

    val ob1 = Observe(obsId, ck3).madd(exp.set(43.3f), repeats.set(4))

    val w1 = Wait(obsId, ck3).add(seconds.set(5).withUnits(UnitsOfMeasure.seconds))

    assert(sc1.info.obsId.obsId == obsId)
    assert(sc1.subsystem == Subsystem.WFOS)
    assert(sc1.prefix == ck1)
    println(s"configkey: ${sc1.itemsetKey}")
    println(s"runId: + ${sc1.info.runId}")


  }

}
