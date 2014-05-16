// This file is interpreted by the sequencer REPL at startup to import the environment

import akka.actor._
import akka.util.Timeout
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import org.tmt.csw.util.Configuration
import java.io.StringReader
import org.tmt.csw.cmd.akka._
import org.tmt.csw.ls.LocationServiceActor.ServiceId
import org.tmt.csw.ls.{LocationServiceActor, LocationService}
import LocationServiceActor._
import scala.concurrent.{Await, Future}
import akka.pattern.ask
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

// Utility functions, shortcuts
import org.tmt.csw.apps.sequencer.Seq
import Seq._

// force loading of Seq class on startup
system.name
