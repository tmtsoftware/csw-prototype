package org.tmt.csw.cmd.core

import org.scalatest.FunSuite
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

/**
 * Tests Configurations
 */
class TestConfiguration extends FunSuite {

  test("Test creating a config in code using java.util.Map") {
    val simplePathMapValue = Map("x.y" -> 4, "z" -> 5).asInstanceOf[Map[String, AnyRef]].asJava
    val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

    val conf = Configuration(pathMapValue)

    assert(2 == conf.size)
    assert(4 == conf.getInt("b.x.y"))
    assert(5 == conf.getInt("b.z"))
    assert(1 == conf.getInt("a.c"))

    assert("b{z=5,x{y=4}},a{c=1}" == conf.toString)
    assert("z=5,x{y=4}" == conf.getConfig("b").toString)
  }

  test("Test creating a config in code using java Maps and Lists") {
    val simplePathMapValue = Map("x.y" -> List(1,2,3).asJava, "z" -> List("a","b","c").asJava).asJava
    val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

    val conf = Configuration(pathMapValue)

    assert(2 == conf.size)
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

    assert(2 == conf.size)
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
          "info" -> Map("configId" -> 1000233, "obsId" -> "TMT-2021A-C-2-1"),
          "tmt.tel.base.pos" -> Map("posName" -> "NGC738B", "c1" -> "22:35:58.530", "c2" -> "33:57:55.40", "equinox" -> "J2000"),
          "tmt.tel.ao.pos.one" -> Map("c1" -> "22:356:01.066", "c2" -> "33:58:21.69", "equinox" -> "J2000")
        )
      )
    )

    assert("TMT-2021A-C-2-1" == conf.getString("config.info.obsId"))
    assert("NGC738B" == conf.getString("config.tmt.tel.base.pos.posName"))
    assert("1000233" == conf.getString("config.info.configId"))
  }

  test("Test with scala maps, lists, arrays") {
    val conf = Configuration(Map("a.c" -> 1, "b" -> Map("x.y" -> List(1,2,3), "z" -> Array("a","b","c"))))
    assert(2 == conf.size)
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
          "tmt.tel.base.pos" -> Map("posName" -> "NGC738B", "c1" -> "22:35:58.530", "c2" -> "33:57:55.40", "equinox" -> "J2000"),
          "tmt.tel.ao.pos.one" -> Map("c1" -> "22:356:01.066", "c2" -> "33:58:21.69", "equinox" -> "J2000")
        )
      )
    ).withConfigId(1000233).withObsId("TMT-2021A-C-2-1")

    assert("TMT-2021A-C-2-1" == conf.getString("config.info.obsId"))
    assert("NGC738B" == conf.getString("config.tmt.tel.base.pos.posName"))
    assert("1000233" == conf.getString("config.info.configId"))
  }

  test("Wait config") {
    val conf = Configuration.waitConfig(true, 1000233, "TMT-2021A-C-2-1")
    assert("wait{obsId=\"TMT-2021A-C-2-1\",forResume=true,configId=1000233}" == conf.toString)
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
}
