package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.ccs.CommandStatus2._
import csw.services.ccs.{CommandStatus2, HcdController, HcdSingleStatusMatcherActor}
import csw.services.loc.Connection
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}
import csw.util.config.StateVariable._
import csw.util.config.{RunId, StateVariable}

import scala.concurrent.duration._

/**
  * TMT Source Code: 8/26/16.
  */
class TromboneCommandHandler(val connections: Set[Connection], currentStateReceiver: ActorRef, replyTo: Option[ActorRef]) extends Actor with ActorLogging with TromboneStateHandler {
  import TromboneAssembly._
  import TromboneCommandHandler._
  import TromboneHCD._
  import TromboneStateHandler._

  var moveCnt:Int = 0 // for testing

  def receive: Receive = topLevelReceive

  def topLevelReceive = stateReceive orElse topLevelReceivePF
  def topLevelReceivePF:Receive = {
    case ExecSequential(sca, tromboneHCD, replyTo) =>
      val configs = sca.configs
      // Start the first one
      context.become(st(sca, configs, tromboneHCD, CommandStatus2.ExecResults()))
      self ! Start(configs.head)
    case cs:ExecResults =>
      log.info("All complete")
      replyTo.foreach(_ ! cs)

    case x => log.error(s"TromboneCommandHandler received an unknown message: $x")
  }

  def st(sca: SetupConfigArg, configsIn: Seq[SetupConfig], tromboneHCD: Option[ActorRef], cstatusIn: ExecResults):Receive = stateReceive orElse startingReceive(sca, configsIn, tromboneHCD, cstatusIn)

  def startingReceive(sca: SetupConfigArg, configsIn: Seq[SetupConfig], tromboneHCD: Option[ActorRef], cstatusIn: ExecResults): Receive = {

    case Start(sc: SetupConfig) =>
      log.info(s"Starting: $sc")
      doStart(sc, tromboneHCD, Some(context.self))

    case cs:NoLongerValid =>
      log.info(s"Received complete for cmd")

    case x:CommandStatus2.Completedx =>
      log.info("WTF")

      // Save record of sequential successes
      val cstatusOut = cstatusIn //.:+(configsIn.head, cs)
      val configsOut = configsIn.tail
      if (configsOut.isEmpty) {
        // If there are no more in the sequence, return the completion for all and be done
        context.become(receive)
        replyTo.foreach(_ ! cstatusOut)
      } else {
        // If there are more, start the next one and pass the completion status to the next execution
        context.become(st(sca, configsOut, tromboneHCD, cstatusOut))
        self ! Start(configsOut.head)
      }

    case cs:CommandStatus2.Error =>
      log.info(s"Received error: ${cs.message}")
      // Save record of sequential successes
      val cstatusOut = cstatusIn :+ (configsIn.head, cs)
      context.become(receive)
      replyTo.foreach(_ ! cstatusOut)
  }

  def doStart(sc: SetupConfig, tromboneHCD: Option[ActorRef], replyTo: Option[ActorRef]): Unit = {

    sc.configKey match {
      case `initCK` => initStart(sc, tromboneHCD, Some(context.self))
      case `datumCK` => datumStart(sc, tromboneHCD, Some(context.self))
      case `stopCK` => stopStart(sc)
      case `moveCK` => moveStart(sc, tromboneHCD, Some(context.self))
      case `positionCK` => positionStart(sc)
      case `setElevationCK` => setElevationStart(sc)
      case `setAngleCK` => setAngleStart(sc)
      case `followCK` => followStart(sc)
      case x => log.error(s"TromboneCommandHandler:doStart received an unknown message: $x")
    }
  }


  def movingMatcher(demand: DemandState, current: CurrentState): Boolean =
    demand.prefix == current.prefix && current(stateKey).head == TromboneHCD.AXIS_IDLE

  def movingPosMatcher(demand: DemandState, current: CurrentState): Boolean =
    demand.prefix == current.prefix && current(stateKey).head == TromboneHCD.AXIS_IDLE && demand(positionKey).head == current(positionKey).head

  def matchDemandState(demandState: DemandState, hcd: ActorRef, replyTo: Option[ActorRef], runId: RunId,
                        timeout: Timeout = Timeout(60.seconds),
                        matcher: Matcher = StateVariable.defaultMatcher): Unit = {
    var stateMatcherActor: Option[ActorRef] = None
    // Cancel any previous state matching, so that no timeout errors are sent to the replyTo actor
    replyTo.foreach { actorRef =>
      // Wait for the demand states to match the current states, then reply to the sender with the command status
      val props = HcdSingleStatusMatcherActor.props(demandState, hcd, actorRef, runId, timeout, matcher)
      stateMatcherActor = Some(context.actorOf(props))
    }
  }

  def initStart(sc: SetupConfig, tromboneHCD: Option[ActorRef], replyTo: Option[ActorRef]):Unit = {
    val ds = DemandState(axisStateCK)
    matchDemandState(ds, tromboneHCD.get, Some(context.self), RunId("test"), 5.seconds, movingMatcher)
  }
  def datumStart(sc: SetupConfig, tromboneHCD: Option[ActorRef], replyTo: Option[ActorRef]) = {
    if (cmd == cmdUninitialized) println("Invalid")
    val ds = DemandState(axisStateCK)
    log.debug(s"Forwarding $sc to $tromboneHCD")
    state(cmd = cmdBusy, move=moveIndexing)
    tromboneHCD.foreach(_ ! HcdController.Submit(SetupConfig(axisDatumCK)))
    matchDemandState(ds, tromboneHCD.get, replyTo, RunId("test"), 10.seconds, movingMatcher)
  }
  def stopStart(sc: SetupConfig) = ???

  def moveStart(sc: SetupConfig, tromboneHCD: Option[ActorRef], replyTo: Option[ActorRef]) = {
    val newPosition = sc(stagePositionKey).head.toInt
    val ds = DemandState(axisStateCK).add(positionKey -> newPosition)
    log.info("DS: " + ds)
    log.debug(s"Forwarding $sc to $tromboneHCD")
    tromboneHCD.foreach(_ ! HcdController.Submit(SetupConfig(axisMoveCK).add(positionKey -> newPosition)))
    matchDemandState(ds, tromboneHCD.get, replyTo, RunId(s"test$moveCnt"), 10.seconds, movingPosMatcher)
    moveCnt += 1
  }
  def positionStart(sc: SetupConfig) = ???
  def setElevationStart(sc: SetupConfig) = ???
  def setAngleStart(sc: SetupConfig) = ???
  def followStart(sc: SetupConfig) = ???
}

object TromboneCommandHandler extends LazyLogging {

  def props(connections: Set[Connection], currentStateReceiver: ActorRef, replyTo: Option[ActorRef]) = Props(new TromboneCommandHandler(connections, currentStateReceiver, replyTo))

  // Messages received by TromboneCommandHandler
  case class ExecSequential(sca: SetupConfigArg, tromboneHCD: Option[ActorRef], replyTo: Option[ActorRef])

  case class Start(sc: SetupConfig)
/*
  type ExecResult = (SetupConfig, CommandStatus)
  case class ExecResults(results: List[ExecResult] = List.empty[ExecResult]) {
    def :+(pair: ExecResult) = ExecResults(results = results :+ pair)
  }
  */

}

/**
  * This method can be called from the setup method to distribute parts of the configs to HCDs based on the
  * prefix. If the prefix of a SetupConfig matches the one for the HCD, it is sent to that HCD.
  *
  * @param locationsResolved true if the locations of all the assembly's required services (HCDs) have been resolved
  * @param configArg         contains one or more configs
  * @param replyTo           send the command status (Completed) to this actor when all the configs are "matched" or an error status if a timeout occurs
  * @return Valid if locationsResolved, otherwise Invalid
  */
/*
  protected def distributeSetupConfigs(locationsResolved: Boolean, configArg: SetupConfigArg,
                                       replyTo: Option[ActorRef]): Validation = {
    if (locationsResolved) {
      val pairs = for {
        config <- configArg.configs
        actorRef <- getActorRefs(config.prefix)
      } yield {
        log.debug(s"Forwarding $config to $actorRef")
        actorRef ! HcdController.Submit(config)
        (actorRef, DemandState(config))
      }
      val hcds = pairs.map(_._1).toSet
      val demandStates = pairs.map(_._2)
  //    matchDemandStates(demandStates, hcds, replyTo, configArg.info.runId)
      Valid
    } else {
      val s = "Unresolved locations for one or more HCDs"
      log.error(s)
      Invalid(UnresolvedLocationsIssue(s))
    }
  }
  */

/*
  def matchDemandStates(demandStates: Seq[DemandState], hcds: Set[ActorRef], replyTo: Option[ActorRef], runId: RunId,
                        timeout: Timeout = Timeout(60.seconds),
                        matcher: Matcher = StateVariable.defaultMatcher): Unit = {
    var stateMatcherActor: Option[ActorRef] = None
    // Cancel any previous state matching, so that no timeout errors are sent to the replyTo actor
    //stateMatcherActor.foreach(context.stop)
    replyTo.foreach { actorRef =>
      // Wait for the demand states to match the current states, then reply to the sender with the command status
      val props = HcdStatusMatcherActor.props(demandStates.toList, hcds, actorRef, runId, timeout, matcher, this.prefix)
      stateMatcherActor = Some(context.actorOf(props))
    }
  }
*/
/*
  protected def getActorRefs(targetPrefix: String): Set[ActorRef] = {
    val x = getLocations.collect {
      case r @ ResolvedAkkaLocation(connection, uri, prefix, actorRefOpt) if prefix == targetPrefix => actorRefOpt
    }
    x.flatten
  }
  */