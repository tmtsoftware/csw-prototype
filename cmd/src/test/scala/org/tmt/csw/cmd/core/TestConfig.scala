package org.tmt.csw.cmd.core

import org.scalatest.FunSuite
import java.io.StringReader
import scala.collection.JavaConverters._
import com.typesafe.config._

import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import com.typesafe.config.ConfigException.WrongType

object TestConfig {
  val testConfig =
    """
      |      config {
      |        info {
      |          configId = 1000233
      |          obsId = TMT-2021A-C-2-1
      |        }
      |        tmt.tel.base.pos {
      |          posName = NGC738B
      |          c1 = "22:35:58.530"
      |          c2 = "33:57:55.40"
      |          equinox = J2000
      |        }
      |        tmt.tel.ao.pos.one {
      |          c1 = "22:356:01.066"
      |          c2 = "33:58:21.69"
      |          equinox = J2000
      |        }
      |      }
      |
    """.stripMargin
}

/**
 * Test the Confog object
  */
class TestConfig extends FunSuite {


  test("Test using the Akka Config classes to parse a config from a string") {
    val conf = ConfigFactory.parseReader(new StringReader(TestConfig.testConfig))
    println(conf.toString)
    assert(conf.getInt("config.info.configId") == 1000233)
    val pos = conf.getConfig("config.tmt.tel.base.pos")
    assert(pos.getString("posName") == "NGC738B")
    assert(conf.getString("config.tmt.tel.base.pos.posName") == "NGC738B")
    assert(conf.getString("config.tmt.tel.base.pos.equinox") == "J2000")
    assert(conf.getString("config.tmt.tel.ao.pos.one.c2") == "33:58:21.69")
    assert(conf.getString("config.tmt.tel.ao.pos.one.equinox") == "J2000")
  }

  test("Test Akka Config class extracting single values while keeping the hierarchy") {
    val conf = ConfigFactory.parseReader(new StringReader(TestConfig.testConfig))
    val path = "config.tmt.tel.base.pos.posName"
    val x = try {
      conf.getConfig(path)
    } catch {
      case e: WrongType =>
        val ar = path.split('.')
        val parentPath  = ar.init.mkString(".")
        val key = ar.last
        conf.getConfig(parentPath).withOnlyPath(key)
    }
    println(x.toString)
  }

  test("Test creating a config in code") {
    val simplePathMapValue = Map("x.y" -> 4, "z" -> 5).asInstanceOf[Map[String, AnyRef]].asJava
    val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

    val conf = ConfigFactory.parseMap(pathMapValue)

    assert(2 == conf.root.size)
    assert(4 == conf.getInt("b.x.y"))
    assert(5 == conf.getInt("b.z"))
    assert(1 == conf.getInt("a.c"))

    val options = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
    assert("b{z=5,x{y=4}},a{c=1}" == conf.root.render(options))
    assert("z=5,x{y=4}" == conf.root.get("b").render(options))
  }


  // See https://github.com/json4s/json4s#readme
  test("Test inline creation with JSON DSL") {
    val jsonString = """
      |   "config":{
      |      "info":{
      |        "configId":1000233,
      |        "obsId":"TMT-2021A-C-2-1"
      |      },
      |      tmt.tel.base.pos:{
      |        "posName":"NGC738B",
      |        "c1":"22:35:58.530",
      |        "c2":"33:57:55.40",
      |        "equinox":"J2000"
      |      },
      |      tmt.tel.ao.pos.one:{
      |        "c1":"22:356:01.066",
      |        "c2":"33:58:21.69",
      |        "equinox":"J2000"
      |      }
      |    }
      |
    """.stripMargin

    // XXX Note: Using the JSON DSL makes it impossible to have unquoted keys (quotes make a difference in keys)
    val json =
      "config" ->
        ("info" ->
          ("configId" -> 1000233) ~
            ("obsId" -> "TMT-2021A-C-2-1")
          ) ~
          ("tmt.tel.base.pos" ->
            ("posName" -> "NGC738B") ~
              ("c1" -> "22:35:58.530") ~
              ("c2" -> "33:57:55.40") ~
              ("equinox" -> "J2000")
            ) ~
          ("tmt.tel.ao.pos.one" ->
            ("c1" -> "22:356:01.066") ~
              ("c2" -> "33:58:21.69") ~
              ("equinox" -> "J2000")
            )
    val s = pretty(render(json))
    println("JSON from DSL:")
    println(s)

    val parseOptions = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)
//    val conf = ConfigFactory.parseReader(new StringReader(s), parseOptions)
    val conf = ConfigFactory.parseReader(new StringReader(jsonString), parseOptions)

    assert(conf.getInt("config.info.configId") == 1000233)

    val renderOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(true)
    println("Config from JSON:")
    println(conf.root.render(renderOptions))

    // XXX Doesn't work as expected because "tmt.tel.base.pos" is treated as a single key by json4s
//    assert(conf.getString("config.\"tmt.tel.base.pos\".posName") == "NGC738B")
    assert(conf.getString("config.tmt.tel.base.pos.posName") == "NGC738B")
    assert(conf.getString("config.tmt.tel.base.pos.equinox") == "J2000")
    assert(conf.getString("config.tmt.tel.ao.pos.one.c2") == "33:58:21.69")
    assert(conf.getString("config.tmt.tel.ao.pos.one.equinox") == "J2000")
  }
}
