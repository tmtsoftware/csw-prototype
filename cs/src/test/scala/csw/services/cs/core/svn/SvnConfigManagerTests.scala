package csw.services.cs.core.svn

import akka.actor.ActorSystem
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import org.scalatest.FunSuite
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.io.SVNRepositoryFactory

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by abrighto on 29/02/2016.
  */
class SvnConfigManagerTests extends FunSuite with LazyLogging {
//  implicit val system = ActorSystem()

  test("Test creating a SvnConfigManager, storing and retrieving some files") {
  }

}

