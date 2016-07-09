package javacsw.services.alarms;


import csw.services.alarms.AlarmState;
import csw.services.alarms.AlarmState.ShelvedState;

/**
 * Java access to AlarmState values
 */
@SuppressWarnings("unused")
public class JAlarmState {

    /**
     * Java access to SheledState values
     */
    public static class JShelvedState {
        public static final ShelvedState Shelved = JAlarmService.JShelvedStateSup$.MODULE$.Shelved();
        public static final ShelvedState Normal = JAlarmService.JShelvedStateSup$.MODULE$.Normal();
    }

    /**
     * Java access to ActivationState values
     */
    public static class JActivationState {
        public static final AlarmState.ActivationState OutOfService = JAlarmService.JActivationStateSup$.MODULE$.OutOfService();
        public static final AlarmState.ActivationState Normal = JAlarmService.JActivationStateSup$.MODULE$.Normal();
    }

    /**
     * Java access to LatchedState values
     */
    public static class JLatchedState {
        public static final AlarmState.LatchedState NeedsReset = JAlarmService.JLatchedStateSup$.MODULE$.NeedsReset();
        public static final AlarmState.LatchedState Normal = JAlarmService.JLatchedStateSup$.MODULE$.Normal();
    }

    /**
     * Java access to AcknowledgedState values
     */
    public static class JAcknowledgedState {
        public static final AlarmState.AcknowledgedState NeedsAcknowledge = JAlarmService.JAcknowledgedStateSup$.MODULE$.NeedsAcknowledge();
        public static final AlarmState.AcknowledgedState Normal = JAlarmService.JAcknowledgedStateSup$.MODULE$.Normal();
    }
}
