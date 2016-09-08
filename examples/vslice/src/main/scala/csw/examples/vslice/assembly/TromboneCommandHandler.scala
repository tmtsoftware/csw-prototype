package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.assembly.TromboneControl.TromboneControlConfig
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.CommandStatus2._
import csw.services.ccs.SequentialExecution.SequentialExecutor.StartTheDamnThing
import csw.services.ccs.StateMatchers.MultiStateMatcherActor.StartMatch
import csw.services.ccs.StateMatchers.{DemandMatcher, MultiStateMatcherActor, StateMatcher}
import csw.services.ccs.{CommandStatus2, HcdController, SequentialExecution}
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}
import csw.util.config.StateVariable._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * TMT Source Code: 8/26/16.
  */
class TromboneCommandHandler(controlConfig: TromboneControlConfig, currentStateSource: ActorRef, replyTo: Option[ActorRef]) extends Actor with ActorLogging with TromboneStateHandler {

  import TromboneAssembly._
  import TromboneCommandHandler._
  import TromboneStateHandler._

  var moveCnt: Int = 0 // for testing

  def receive: Receive = topLevelReceive

  def topLevelReceive = stateReceive orElse waitingReceive

  def waitingReceive: Receive = {
    case ExecSequential(sca, tromboneHCD) =>
      /*
      val configs = sca.configs
      // Start the first one
      context.become(st(sca, configs, tromboneHCD, CommandStatus2.ExecResults()))
      self ! Start(configs.head)
      */
      val se = context.actorOf(SequentialExecution.SequentialExecutor.props(sca, doStart, tromboneHCD, replyTo))
      se ! StartTheDamnThing

    case x => log.error(s"TromboneCommandHandler:waitingReceive received an unknown message: $x")
  }

  def doStart(sc: SetupConfig, tromboneHCD: ActorRef, replyTo: Option[ActorRef]): Unit = {

    sc.configKey match {
      case `initCK` =>
        if (cmd == cmdUninitialized) {
          println("Invalid")
          replyTo.foreach(_ ! CommandStatus2.NoLongerValid(OtherIssue("what")))
        } else {
          log.debug(s"Forwarding $sc to $tromboneHCD")
          state(cmd = cmdBusy, move = moveIndexing)
          executeOne4(tromboneHCD, SetupConfig(initCK), idleMatcher, replyTo) {
            case Completed =>
              state(cmd = cmdReady, move = moveIndexed, sodiumLayer = false, nss = false)
            case Error(message) =>
              println("Error")
          }
        }


      case `datumCK` =>
        if (cmd != cmdUninitialized) {
          replyTo.foreach(_ ! CommandStatus2.NoLongerValid(OtherIssue("what")))
        } else {
          state(cmd = cmdBusy, move = moveIndexing)
          executeOne4(tromboneHCD, SetupConfig(axisDatumCK), idleMatcher, replyTo) {
            case Completed =>
              state(cmd = cmdReady, move = moveIndexed, sodiumLayer = false, nss = false)
            case Error(message) =>
              println("Error")
          }

        }

      case `stopCK` =>
        if (cmd == cmdUninitialized) {
          println("Invalid")
          replyTo.foreach(_ ! CommandStatus2.NoLongerValid(OtherIssue("what")))
        } else {

          val currentMove = move
          state(cmd = cmdBusy)
          executeOne4(tromboneHCD, cancelSC, idleMatcher, replyTo) {
            case Completed =>
              val finalMoveState = currentMove match {
                case `moveIndexing` => moveUnindexed
                case `moveMoving` => moveIndexed
                case _ => moveUnindexed // Error
              }
              state(cmd = cmdReady, move = finalMoveState)
            case Error(message) =>
              println("Error stop")
          }
        }

      case `moveCK` =>
        // Move moves the trombone state in mm but not encoder units
        //if (cmd == cmdUninitialized) println("Invalid")
        if (cmd == cmdUninitialized) {
          println("Invalid")
          replyTo.foreach(_ ! NoLongerValid(OtherIssue("Whatwhat")))
        } else {

          val stagePosition = sc(stagePositionKey)

          // Convert to encoder units
          val encoderPosition = Algorithms.rangeDistanceTransform(controlConfig, stagePosition)
          // First convert the
          println("Encoder position:" + encoderPosition)

          log.info(s"Setting trombone current to: ${encoderPosition}")

          val stateMatcher = posMatcher(encoderPosition)
          // Position key is encoder units
          val scOut = SetupConfig(axisMoveCK).add(positionKey -> encoderPosition)
          state(cmd = cmdBusy, move = moveMoving)

          executeOne4(tromboneHCD, scOut, stateMatcher, replyTo) {
            case Completed =>
              state(cmd = cmdReady, move = moveIndexed)
              moveCnt += 1
              log.info(s"Done with move at: $moveCnt")
            case Error(message) =>
              log.error(s"Move command failed with message: $message")
          }
        }

      case `positionCK` => positionStart(sc)
      case `setElevationCK` => setElevationStart(sc)
      case `setAngleCK` => setAngleStart(sc)
      case `followCK` => followStart(sc)
      case x => log.error(s"TromboneCommandHandler:doStart received an unknown message: $x")
    }
  }



/*
    def validate(testCondition: => Validation)(trueCodeBlock: => Unit) = {
      /*
      testCondition match {
        case Invalid(issue) =>
          self ! NoLongerValid(issue)
      }
      */
      if (testCondition == Invalid) {
        self ! NoLongerValid(testCondition.asInstanceOf[Invalid].issue)
      } else {
        trueCodeBlock
      }
    }
*/


  def executeOne4(destination: ActorRef, setupConfig: SetupConfig, stateMatcher: StateMatcher, replyTo: Option[ActorRef] = None,
                  timeout: Timeout = Timeout(5.seconds))(codeBlock: PartialFunction[CommandStatus2, Unit]): Unit = {
    import context.dispatcher
    implicit val t = Timeout(timeout.duration + 1.seconds)

    destination ! HcdController.Submit(setupConfig)
    val matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout))
    for {
      cmdStatus <- (matcher ? StartMatch(stateMatcher)).mapTo[CommandStatus2]
    } {
      codeBlock(cmdStatus)
      replyTo.foreach(_ ! cmdStatus)
    }
  }

  def executeOne5(destination: ActorRef, setupConfig: SetupConfig, stateMatcher: StateMatcher, replyTo: Option[ActorRef] = None,
                  timeout: Timeout = Timeout(5.seconds))(validationCodeBlock: => Validation)(codeBlock: PartialFunction[CommandStatus2, Unit]): Unit = {
    import context.dispatcher
    implicit val t = Timeout(timeout.duration + 1.seconds)

    validationCodeBlock match {
      case Invalid(issue) => self ! NoLongerValid(issue)
      case Valid =>

        destination ! HcdController.Submit(setupConfig)
        val matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout))
        for {
          cmdStatus <- (matcher ? StartMatch(stateMatcher)).mapTo[CommandStatus2]
        } {
          codeBlock(cmdStatus)
          //replyTo.foreach(_ ! cmdStatus)
          self ! cmdStatus
        }
    }
  }


  def idleMatcher: DemandMatcher = DemandMatcher(DemandState(axisStateCK).add(stateKey -> TromboneHCD.AXIS_IDLE))

  def posMatcher(position: Int): DemandMatcher =
    DemandMatcher(DemandState(axisStateCK).madd(stateKey -> TromboneHCD.AXIS_IDLE, positionKey -> position))


  def initStart(sc: SetupConfig, tromboneHCD: ActorRef, replyTo: Option[ActorRef]): Unit = {
    import context.dispatcher

    implicit val timeout = Timeout(5.seconds)

    val matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout))
    val f: Future[CommandStatus2] = (matcher ? StartMatch(idleMatcher)).mapTo[CommandStatus2]
    replyTo.foreach(f.pipeTo(_))
  }

  def stopStart(sc: SetupConfig) = {
    if (cmd == cmdUninitialized) println("Invalid")
    if (move != moveIndexed && move != moveMoving) println("Invalid")

  }

  def positionStart(sc: SetupConfig) = ???

  def setElevationStart(sc: SetupConfig) = {
    if (cmd == cmdUninitialized) println("Invalid")

  }

  def setAngleStart(sc: SetupConfig) = {
    if (cmd == cmdUninitialized) println("Invalid")

  }

  def followStart(sc: SetupConfig) = ???
}

object TromboneCommandHandler extends LazyLogging {

  def props(controlConfig: TromboneControlConfig, currentStateReceiver: ActorRef, replyTo: Option[ActorRef]) = Props(new TromboneCommandHandler(controlConfig, currentStateReceiver, replyTo))

  // Messages received by TromboneCommandHandler
  case class ExecSequential(sca: SetupConfigArg, tromboneHCD: ActorRef)

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

/*
  def st(sca: SetupConfigArg, configsIn: Seq[SetupConfig], tromboneHCD: ActorRef, execResultsIn: ExecResults):Receive =
    stateReceive orElse executingReceive(sca, configsIn, tromboneHCD, execResultsIn)

  def executingReceive(sca: SetupConfigArg, configsIn: Seq[SetupConfig], tromboneHCD: ActorRef, execResultsIn: ExecResults): Receive = {

    case Start(sc: SetupConfig) =>
      log.info(s"Starting: $sc")
      doStart(sc, tromboneHCD, Some(context.self))

    case cs @ NoLongerValid(issue) =>
      log.info(s"Received complete for cmd: $issue + $cs" )
      // Save record of sequential successes
      context.become(receive)
      val execResultsOut = execResultsIn :+ (cs, configsIn.head)
      replyTo.foreach(_ ! CommandResult(sca.info.runId, Incomplete, execResultsOut))

    case cs@CommandStatus2.Completed =>

      // Save record of sequential successes
      val execResultsOut = execResultsIn :+(cs, configsIn.head)
      val configsOut = configsIn.tail
      if (configsOut.isEmpty) {
        // If there are no more in the sequence, return the completion for all and be done
        context.become(receive)
        replyTo.foreach(_ ! CommandResult(sca.info.runId, Completed, execResultsOut))
      } else {
        // If there are more, start the next one and pass the completion status to the next execution
        context.become(st(sca, configsOut, tromboneHCD, execResultsOut))
        self ! Start(configsOut.head)
      }

    case cs:CommandStatus2.Error =>
      log.info(s"Received error: ${cs.message}")
      // Save record of sequential successes
      val execResultsOut = execResultsIn :+ (cs, configsIn.head)
      context.become(receive)
      replyTo.foreach(_ ! CommandResult(sca.info.runId, Incomplete, execResultsOut))
  }

*/

/*
def executeOne(destination: ActorRef, setupConfig: SetupConfig, stateMatcher: StateMatcher, replyTo: Option[ActorRef] = None, timeout: Timeout = Timeout(5.seconds)) = {
    import context.dispatcher
    implicit val t = Timeout(timeout.duration + 1.seconds)

    destination ! HcdController.Submit(setupConfig)
    val matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout))
    replyTo.foreach(rt => (matcher ? StartMatch(stateMatcher)).mapTo[CommandStatus2].pipeTo(rt))
  }
 */