// This file is interpreted by the sequencer REPL at startup to import the environment

import akka.actor._
import akka.util.Timeout
import csw.services.loc.LocationService

import scala.collection.JavaConverters._
import java.io.StringReader

import csw.services.ccs._
import csw.util.param._
import csw.util.param.Parameters._
import csw.util.param.Events._

import scala.concurrent.{Await, Future}
import akka.pattern.ask

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import csw.services.loc.ComponentId
import csw.services.pkg.Component.AssemblyInfo
import csw.services.sequencer.SequencerEnv
import csw.util.param.UnitsOfMeasure._

// Utility functions, shortcuts
import SequencerEnv._

// force loading of Seq class on startup
system.name
