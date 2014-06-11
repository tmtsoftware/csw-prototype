package csw.util

import org.scalatest.FunSuite
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import com.typesafe.config.ConfigException.ValidationFailed
import Configuration._

/**
 * Tests Configurations
 */
class TestConfiguration extends FunSuite {

  test("Test creating a config in code using java.util.Map") {
    val simplePathMapValue = Map("x.y" -> 4, "z" -> 5).asInstanceOf[Map[String, AnyRef]].asJava
    val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

    val conf = Configuration(pathMapValue)

    assert(4 == conf.getInt("b.x.y"))
    assert(5 == conf.getInt("b.z"))
    assert(1 == conf.getInt("a.c"))

    assert("b{z=5,x{y=4}},a{c=1}" == conf.toString)
    assert("z=5,x{y=4}" == conf.getConfig("b").toString)
  }

  test("Test creating a config from an array of bytes") {
    val simplePathMapValue = Map("x.y" -> 4, "z" -> 5).asInstanceOf[Map[String, AnyRef]].asJava
    val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

    val conf1 = Configuration(pathMapValue)
    val conf = Configuration(conf1.toString.getBytes)

    assert(4 == conf.getInt("b.x.y"))
    assert(5 == conf.getInt("b.z"))
    assert(1 == conf.getInt("a.c"))

    assert("b{z=5,x{y=4}},a{c=1}" == conf.toString)
    assert("z=5,x{y=4}" == conf.getConfig("b").toString)
  }

  test("Test creating a config in code using java Maps and Lists") {
    val simplePathMapValue = Map("x.y" -> List(1, 2, 3).asJava, "z" -> List("a", "b", "c").asJava).asJava
    val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

    val conf = Configuration(pathMapValue)

    assert(1 == conf.getIntList("b.x.y").get(0))
    assert(2 == conf.getIntList("b.x.y").get(1))
    assert(3 == conf.getIntList("b.x.y").get(2))
    assert(List("a", "b", "c") == (for (i <- conf.getStringList("b.z")) yield i))
    assert(1 == conf.getInt("a.c"))
    assert("b{z=[a,b,c],x{y=[1,2,3]}},a{c=1}" == conf.toString)
    assert("z=[a,b,c],x{y=[1,2,3]}" == conf.getConfig("b").toString)
  }

  test("Test creating a config using a scala.Map") {
    val pathMapValue = Map("a.c" -> 1, "b" -> Map("x.y" -> 4, "z" -> 5))

    val conf = Configuration(pathMapValue)

    assert(4 == conf.getInt("b.x.y"))
    assert(5 == conf.getInt("b.z"))
    assert(1 == conf.getInt("a.c"))

    assert("b{z=5,x{y=4}},a{c=1}" == conf.toString)
    assert("z=5,x{y=4}" == conf.getConfig("b").toString)
  }

  test("Another test with scala.map") {
    val conf = Configuration(
      Map("config" ->
        Map(
          "info" -> Map("obsId" -> "TMT-2021A-C-2-1"),
          "tmt.tel.base.pos" -> Map("posName" -> "NGC738B", "c1" -> "22:35:58.530", "c2" -> "33:57:55.40", "equinox" -> "J2000"),
          "tmt.tel.ao.pos.one" -> Map("c1" -> "22:356:01.066", "c2" -> "33:58:21.69", "equinox" -> "J2000")
        )
      )
    )

    assert("config" == conf.rootKey().get)
    assert("tel" == conf.getConfig("config.tmt").rootKey().get)
    assert("TMT-2021A-C-2-1" == conf.getString("config.info.obsId"))
    assert("NGC738B" == conf.getString("config.tmt.tel.base.pos.posName"))
    assert("22:35:58.530" == conf.getString("config.tmt.tel.base.pos.c1"))
    assert("33:57:55.40" == conf.getString("config.tmt.tel.base.pos.c2"))
    assert("J2000" == conf.getString("config.tmt.tel.base.pos.equinox"))
    assert("22:356:01.066" == conf.getString("config.tmt.tel.ao.pos.one.c1"))
    assert("33:58:21.69" == conf.getString("config.tmt.tel.ao.pos.one.c2"))
    assert("J2000" == conf.getString("config.tmt.tel.ao.pos.one.equinox"))
  }

  test("Test with scala maps, lists, arrays") {
    val conf = Configuration(Map("a.c" -> 1, "b" -> Map("x.y" -> List(1, 2, 3), "z" -> Array("a", "b", "c"))))
    assert(1 == conf.getIntList("b.x.y").get(0))
    assert(2 == conf.getIntList("b.x.y").get(1))
    assert(3 == conf.getIntList("b.x.y").get(2))
    assert(List("a", "b", "c") == (for (i <- conf.getStringList("b.z")) yield i))
    assert(1 == conf.getInt("a.c"))
    assert("b{z=[a,b,c],x{y=[1,2,3]}},a{c=1}" == conf.toString)
    assert("z=[a,b,c],x{y=[1,2,3]}" == conf.getConfig("b").toString)
  }

  test("Test adding configId and obsId to a config") {
    val conf = Configuration(
      Map("config" ->
        Map(
          "info" -> Map("configId" -> "1000233", "obsId" -> "TMT-2021A-C-2-1"),
          "tmt.tel.base.pos" -> Map("posName" -> "NGC738B", "c1" -> "22:35:58.530", "c2" -> "33:57:55.40", "equinox" -> "J2000"),
          "tmt.tel.ao.pos.one" -> Map("c1" -> "22:356:01.066", "c2" -> "33:58:21.69", "equinox" -> "J2000")
        )
      )
    )

    assert("TMT-2021A-C-2-1" == conf.getString("config.info.obsId"))
    assert("NGC738B" == conf.getString("config.tmt.tel.base.pos.posName"))
    assert("1000233" == conf.getString("config.info.configId"))
  }

  test("Adding values to a Configuration") {
    val conf = Configuration(
      Map("config" ->
        Map("tmt.tel.base.pos" -> Map("posName" -> "NGC738B", "c1" -> "22:35:58.530", "c2" -> "33:57:55.40", "equinox" -> "J2000"))))

    val map = conf.asMap("config.tmt.tel.base.pos") ++ Map("key1" -> "value1", "key2" -> 42)
    val conf2 = conf.withValue("config.tmt.tel.base.pos", map.toMap)
    assert("value1" == conf2.getString("config.tmt.tel.base.pos.key1"))
    assert(42 == conf2.getInt("config.tmt.tel.base.pos.key2"))

    val conf3 = conf2.withValue("config.tmt.tel.ao.pos.one", Map("c1" -> "22:356:01.066", "c2" -> "33:58:21.69", "equinox" -> "J2000"))

    assert("NGC738B" == conf3.getString("config.tmt.tel.base.pos.posName"))
    assert("22:356:01.066" == conf3.getString("config.tmt.tel.ao.pos.one.c1"))
    assert("33:58:21.69" == conf3.getString("config.tmt.tel.ao.pos.one.c2"))
    assert("J2000" == conf3.getString("config.tmt.tel.ao.pos.one.equinox"))
  }


  test("Test getting config from a simple key: value or a config subtree") {
    val conf = Configuration(
      Map("config" ->
        Map("tmt.tel.base.pos" -> Map("posName" -> "NGC738B", "c1" -> "22:35:58.530", "c2" -> "33:57:55.40", "equinox" -> "J2000"))))

    val posNameConf = conf.getConfig("config.tmt.tel.base.pos.posName")
    val posConf = conf.getConfig("config.tmt.tel.base.pos")
    assert("NGC738B" == posNameConf.getString("posName"))
    assert("NGC738B" == posConf.getString("posName"))
  }

  test("Test merging a list of configurations") {
    val c1 = Configuration().withValue("filter", "GG455")
    val c2 = Configuration().withValue("grating", "T5422")
    val c3 = Configuration.merge(List(c1, c2))
    assert(c3.toString == "grating=T5422,filter=GG455")
  }

  test("Test validation") {
    val conf = Configuration(TestConfig.testConfig)
    val ref = Configuration(TestConfig.refConfig)
    assert(conf.checkValid(ref).isSuccess)

    val t1 = conf.withoutPath("config.tmt.tel.base.pos.posName").checkValid(ref)
    assert(t1.isFailure)
    assert(t1.failed.get.isInstanceOf[ValidationFailed])

    val constraints = List(
      RangeConstraint("config.info.configId", 0, 100),
      EnumConstraint("config.tmt.tel.ao.pos.one.equinox", List("J2000", "B1950"))
    )
    val t2 = conf.checkValid(ref, constraints)
    assert(t2.isFailure)
    println(s"Message = ${t2.failed.get.getMessage}")
    assert(t2.failed.get.isInstanceOf[ValidationFailed])
    assert(t2.failed.get.asInstanceOf[ValidationFailed].problems().toList.size == 1)

    val t3 = conf.withValue("config.tmt.tel.ao.pos.one.equinox", "J1000").checkValid(ref, constraints)
    assert(t3.isFailure)
    println(s"Message = ${t3.failed.get.getMessage}")
    assert(t3.failed.get.isInstanceOf[ValidationFailed])
    assert(t3.failed.get.asInstanceOf[ValidationFailed].problems().toList.size == 2)
  }
}
