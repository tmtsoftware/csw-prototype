package csw.services.apps.csClient

import java.io.File

import org.scalatest.FunSuite

/**
 * Tests CsClient option parsing
 */
class CsClientOptsTests extends FunSuite {

  test("Test CsClient option parsing") {
    val path = new File("foo/bar")
    val file = new File("/tmp/foo/bar")
    val id = "myId"
    val comment = "some text"

    CsClientOpts.parse(Array("get", path.toString, "-o", file.toString, "--id", id)) match {
      case Some(c) =>
        assert(c.subcmd == "get")
        assert(c.path == path)
        assert(c.outputFile == file)
        assert(c.id == Some(id))
      case None =>
        fail()
    }

    CsClientOpts.parse(Array("get", path.toString, "-o", file.toString)) match {
      case Some(c) =>
        assert(c.subcmd == "get")
        assert(c.path == path)
        assert(c.outputFile == file)
        assert(c.id == None)
      case None =>
        fail()
    }

    CsClientOpts.parse(Array("create", path.toString, "-i", file.toString)) match {
      case Some(c) =>
        assert(c.subcmd == "create")
        assert(c.path == path)
        assert(c.inputFile == file)
        assert(!c.oversize)
        assert(c.comment == "")
      case None =>
        fail()
    }

    CsClientOpts.parse(Array("create", path.toString, "-i", file.toString, "-c", comment, "--oversize")) match {
      case Some(c) =>
        assert(c.subcmd == "create")
        assert(c.path == path)
        assert(c.inputFile == file)
        assert(c.oversize)
        assert(c.comment == comment)
      case None =>
        fail()
    }

    CsClientOpts.parse(Array("update", path.toString, "-i", file.toString)) match {
      case Some(c) =>
        assert(c.subcmd == "update")
        assert(c.path == path)
        assert(c.inputFile == file)
        assert(c.comment == "")
      case None =>
        fail()
    }

    CsClientOpts.parse(Array("update", path.toString, "-i", file.toString, "-c", comment)) match {
      case Some(c) =>
        assert(c.subcmd == "update")
        assert(c.path == path)
        assert(c.inputFile == file)
        assert(c.comment == comment)
      case None =>
        fail()
    }

    CsClientOpts.parse(Array("list")) match {
      case Some(c) =>
        assert(c.subcmd == "list")
      case None =>
        fail()
    }

    CsClientOpts.parse(Array("history", path.toString)) match {
      case Some(c) =>
        assert(c.subcmd == "history")
        assert(c.path == path)
      case None =>
        fail()
    }
  }
}