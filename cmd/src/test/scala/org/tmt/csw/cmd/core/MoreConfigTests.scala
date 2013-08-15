package org.tmt.csw.cmd.core

import org.scalatest.FunSuite
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import com.typesafe.config.{ConfigRenderOptions, ConfigFactory}

/**
 * TMT Software - Pasadena, California, USA
 * User: gillies
 * Date: 7/29/13
 * File in: org.tmt.csw.cmd.core
 */
class MoreConfigTests extends FunSuite {

  test("Test creating a config in code using java Maps and Lists") {
    val simplePathMapValue = Map("x.y" -> List(1, 2, 3).asJava, "z" -> List("a", "b", "c").asJava).asJava
    val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

    val conf = Configuration(pathMapValue)

    assert(1 == conf.getIntList("b.x.y").get(0))
    assert(2 == conf.getIntList("b.x.y").get(1))
    assert(3 == conf.getIntList("b.x.y").get(2))
    assert(List("a", "b", "c") == (for (i <- conf.getStringList("b.z")) yield i))
    assert(1 == conf.getInt("a.c"))
    assert("b{z=[a,b,c],x{y=[1,2,3]}},a{c=1}" == conf.toTestString)
    assert("z=[a,b,c],x{y=[1,2,3]}" == conf.getConfig("b").toTestString)
  }

  test("Test creating a config in code") {
    val simplePathMapValue = Map("x.y" -> 4, "z" -> 5).asInstanceOf[Map[String, AnyRef]].asJava
    val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

    val conf = ConfigFactory.parseMap(pathMapValue)
    println("Conf: " + conf)

    val y = conf.root().keySet()
    println("y: " + y)

    val ccc = conf.getConfig("b")
    println("ccc: " + ccc)

    assert(2 == conf.root.size)
    assert(4 == conf.getInt("b.x.y"))
    assert(5 == conf.getInt("b.z"))
    assert(1 == conf.getInt("a.c"))

    val options = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
    assert("b{z=5,x{y=4}},a{c=1}" == conf.root.render(options))
    assert("z=5,x{y=4}" == conf.root.get("b").render(options))
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

    val conf2 = Map("f1" -> "Green", "grating" -> "GG483", "test" -> 22)

    val xx = conf.getConfig("config.tmt.tel")
    //println("xx1: " + xx.toTestString)
    //println("xx2: " + xx.format)

    val conf3 = conf.withValue("tmt.mobie", conf2)
    println("Conf3: " + conf3.format)

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


}
