package javacsw.services.kvs

import csw.services.kvs._
import csw.util.config.Configurations._
import csw.util.config.Events._
import csw.util.config.StateVariable._

/**
 * Java API: Provides abstract base classes for kvs subscribers of the supported types.
 */
object JSubscriber {
  import Implicits._
  abstract class SetupConfigSubscriber extends JAbstractSubscriber[SetupConfig]
  abstract class ObserveConfigSubscriber extends JAbstractSubscriber[ObserveConfig]

  abstract class SetupConfigArgSubscriber extends JAbstractSubscriber[SetupConfigArg]
  abstract class ObserveConfigArgSubscriber extends JAbstractSubscriber[ObserveConfigArg]
  abstract class ControlConfigArgSubscriber extends JAbstractSubscriber[ControlConfigArg]
  abstract class SequenceConfigArgSubscriber extends JAbstractSubscriber[SequenceConfigArg]

  abstract class StatusEventSubscriber extends JAbstractSubscriber[StatusEvent]
  abstract class ObserveEventSubscriber extends JAbstractSubscriber[ObserveEvent]
  abstract class SystemEventSubscriber extends JAbstractSubscriber[SystemEvent]

  abstract class CurrentStateSubscriber extends JAbstractSubscriber[CurrentState]
  abstract class DemandStateSubscriber extends JAbstractSubscriber[DemandState]
  abstract class StateVariableSubscriber extends JAbstractSubscriber[StateVariable]
}
