package javacsw.services.ccs;

import csw.services.ccs.HcdMessages;

/**
 * Java API for HcdMessages
 */
public class JHcdMessages {
  /**
   * Message to subscribe the sender to the HCD's state.
   * The sender will receive [[csw.util.config.StateVariable.CurrentState]] messages from the HCD whenever it's state changes.
   */
  public HcdMessages.HcdMessages Subscribe = HcdMessages.Subscribe$.MODULE$;

  /**
   * Message to unsubscribes from the HCD's state messages.
   */
  public HcdMessages.HcdMessages Unsubscribe = HcdMessages.Unsubscribe$.MODULE$;
}
