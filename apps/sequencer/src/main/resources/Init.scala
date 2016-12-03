// This file is interpreted by the sequencer REPL at startup to import the environment

import akka.actor._
import akka.util.Timeout
import csw.services.loc.LocationService
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import java.io.StringReader
import csw.services.ccs._
import csw.util.config._
import csw.util.config.Configurations._
import csw.util.config.Events._
import scala.concurrent.{Await, Future}
import akka.pattern.ask
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

import csw.services.loc.ComponentId
import csw.services.pkg.Component.AssemblyInfo
import csw.util.config.UnitsOfMeasure._

// Utility functions, shortcuts

import csw.services.apps.sequencer.SequencerEnv
import SequencerEnv._

// force loading of Seq class on startup
system.name
