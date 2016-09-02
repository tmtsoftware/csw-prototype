package csw.services.ccs

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.util.Timeout
import csw.services.ccs.CurrentStateReceiver.{AddCurrentStateHandler, RemoveCurrentStateHandler}
import csw.util.config.StateVariable.{CurrentState, DemandState}

import scala.concurrent.duration._

object StateMatchers {

  /**
    * A StateMatcher provides a prefix and a check method that checks the CurrentState and returns
    * a boolean if completion has occured or false in no completion
    */
  trait StateMatcher {
    def prefix: String
    def check(current: CurrentState):Boolean
  }

  /**
    * The DemandMatcherAll checks for equality between the CurrentState and the DemandState.
    * This is more inclusive than DemandMatcher and may not be used regularly
    * @param demand a DemandState that will be tested for equality with each CurrentState
    */
  case class DemandMatcherAll(demand: DemandState) extends StateMatcher {
    def prefix = demand.prefix
    def check(current: CurrentState): Boolean = demand.items.equals(current.items)
  }

  /**
    * The DemandMatcher checks the CurrentStatus for equality with the items in the DemandState.
    * This version tests for equality so it may not work the best with floating point values.
    * Note: If the withUnits flag is set, the equality check with also compare units. False is the default
    * so normally units are ignored for this purpose.
    * @param demand a DemandState that will provide the items for determining completion with the CurrentState
    * @param withUnits when True, units are compared. When false, units are not compared. Default is false.
    */
  case class DemandMatcher(demand: DemandState, withUnits: Boolean = false) extends StateMatcher {
    import csw.util.config.Item
    def prefix = demand.prefix
    def check(current: CurrentState): Boolean = {
      demand.items.forall { di =>
        val foundItem:Option[Item[_]] = current.find(di)
        foundItem.fold(false)(if(withUnits) _.equals(di) else _.values.equals(di.values))
      }
    }
  }

  /**
    * PresenceMatcher only checks for the existence of a CurrentState with a given prefix.
    * @param prefix the prefix to match against the CurrentState
    */
  case class PresenceMatcher(prefix: String) extends StateMatcher {
    def check(current: CurrentState) = true
  }

  /**
    * Subscribes to the current state values of a single HCD through the CurrentStateReceiver and notifies the
    * sender with the command status when the strea matches the demand state,
    * or with an error status message if the given timeout expires.
    *
    * See props for a description of the arguments for the class and message that starts the match.
    */
  class SingleStateMatcherActor(currentStateReceiver: ActorRef, timeout: Timeout) extends Actor with ActorLogging {
    import SingleStateMatcherActor._
    import context.dispatcher

    def receive: Receive = waiting

    // Here this matcher is subscribing to the stream of CurrentState
    currentStateReceiver ! AddCurrentStateHandler(self)

    // Waiting for all variables to match, which is the case when the results set contains
    // a matching current state for each demand state
    def waiting: Receive = {
      case StartMatch(matcher) =>
        val mysender = sender()
        val timer = context.system.scheduler.scheduleOnce(timeout.duration, self, timeout)
        context.become(executing(matcher, mysender, timer))

      case x => log.error(s"SingelStateMatcherActor received an unexpected message: $x")
    }

    // Waiting for all variables to match, which is the case when the results set contains
    // a matching current state for each demand state
    def executing(matcher: StateMatcher, mysender: ActorRef, timer: Cancellable): Receive = {
      case current: CurrentState =>
        log.debug(s"received current state: $current")
        if (matcher.prefix == current.prefix && matcher.check(current)) {
          timer.cancel()
          mysender ! CommandStatus2.Completed
          currentStateReceiver ! RemoveCurrentStateHandler(self)
          context.stop(self)
        }

      case `timeout` =>
        mysender ! CommandStatus2.Error("Current state matching timed out")
        currentStateReceiver ! RemoveCurrentStateHandler(self)
        context.stop(self)

      case x => log.error(s"SingleStateMatcherActor received an unexpected message: $x")
    }
  }

  object SingleStateMatcherActor {
    /**
      * Props used to create the HcdStatusMatcherActor actor.
      * Precondition: The matcher assumes that the status publishers have been added to the StateReceiver
      *
      * @param currentStateReceiver  a source of CurrentState events
      * @param timeout the amount of time to wait for a match before giving up and replying with a Timeout message
      */
    def props(currentStateReceiver: ActorRef, timeout: Timeout):Props =
    Props(classOf[SingleStateMatcherActor], currentStateReceiver, timeout)

    /**
      * Message class used to start off the execution of the state matcher
      *
      * @param matcher the function used to compare the demand and current states extends StateMatcher trait.
      */
    case class StartMatch(matcher: StateMatcher)

  }

  /**
    * Subscribes to the current state values of a set of HCDs through the CurrentStateReceiver and notifies the
    * sender with the command status when they all match the respective demand states,
    * or with an error status message if the given timeout expires.
    *
    * See props for a description of the arguments for the class and message that start the match.
    */
  class MultiStateMatcherActor(currentStateReceiver: ActorRef, timeout: Timeout) extends Actor with ActorLogging {

    import MultiStateMatcherActor._
    import context.dispatcher

    def receive: Receive = waiting

    // This subscribes this
    currentStateReceiver ! AddCurrentStateHandler(self)

    // Waiting for all variables to match, which is the case when the results set contains
    // a matching current state for each demand state
    def waiting: Receive = {
      case StartMatch(matchers) =>
        val mysender = sender()
        val timer = context.system.scheduler.scheduleOnce(timeout.duration, self, timeout)
        context.become(executing(matchers, mysender, timer))

      case x => log.error(s"MultiStateMatcherActor received an unexpected message: $x")
    }

    // Waiting for all variables to match, which is the case when the results set contains
    // a matching current state for each demand state
    def executing(matchers: List[StateMatcher], mysender: ActorRef, timer: Cancellable): Receive = {
      case current: CurrentState =>
        log.debug(s"received current state: $current")
        // filter the matchers first on prefix and then on check function to get only matchers that succeed
        val matched = matchers.filter(_.prefix == current.prefix).filter(_.check(current))
        if (matched.nonEmpty) {
          log.debug("MultiStateMatcherActor matched")
          // Note that this accomodates the case when more than one matcher match on the same prefix!
          val newMatchers = matchers.diff(matched)
          if (newMatchers.isEmpty) {
            timer.cancel()
            currentStateReceiver ! RemoveCurrentStateHandler(self)
            mysender ! CommandStatus2.Completed
            context.stop(self)
          } else {
            // Call again with a smaller list of demands!
            context.become(executing(newMatchers, mysender, timer))
          }
        }

      case `timeout` =>
        log.debug(s"received timeout")
        mysender ! CommandStatus2.Error("MultiStateMatcherActor state matching timed out")
        currentStateReceiver ! RemoveCurrentStateHandler(self)
        context.stop(self)

      case x => log.error(s"MultiStateMatcherActor received an unexpected message: $x")
    }
  }

  object MultiStateMatcherActor {
    /**
      * Props used to create the HcdStatusMultiMatcherActor actor.
      *
      * @param currentStateReceiver a source of CurrentState events
      * @param timeout              the amount of time to wait for a match before giving up and replying with a Timeout message
      */
    def props(currentStateReceiver: ActorRef, timeout: Timeout = Timeout(60.seconds)): Props =
            Props(classOf[MultiStateMatcherActor], currentStateReceiver, timeout)

    /**
      * Props used to create the MultiStateMatcherActor actor.
      *
      * @param matcher the a list of StateMatcher instances used to compare the demand and current states
      */
    case class StartMatch(matcher: List[StateMatcher])

    object StartMatch {
      def apply(matchers: StateMatcher*):StartMatch = StartMatch(matchers.toList)
    }

  }

}