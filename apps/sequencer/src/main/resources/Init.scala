// This file is interpreted by the sequencer REPL at startup to import the environment

import akka.actor._
import akka.util.Timeout
import csw.services.ls.LocationServiceActor
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import java.io.StringReader
import csw.services.cmd_old.akka._
import LocationServiceActor.ServiceId
import csw.services.ls.LocationServiceActor
import LocationServiceActor._
import scala.concurrent.{Await, Future}
import akka.pattern.ask
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

// Utility functions, shortcuts

import csw.services.apps.sequencer.Seq
import Seq._

// force loading of Seq class on startup
system.name
