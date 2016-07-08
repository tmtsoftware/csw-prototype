package javacsw.services.alarms;

import csw.services.alarms.AlarmKey;
import csw.services.alarms.AlarmKey$;
import csw.services.alarms.AlarmModel;

import java.util.Optional;

/**
 * Java API for creating AlarmKey instances
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
class JAlarmKey {
    /**
     * Creates an alarm key from the information in the model
     *
     * @param a the alarm model
     * @return the alarm key
     */
    static AlarmKey create(AlarmModel a) {
        return AlarmKey$.MODULE$.apply(a);
    }

    /**
     * Creates an alarm key from the given optional subsystem, component and name, using wildcards in place of None.
     * The key may match multiple alarms if any of the arguments are empty or contain Redis wildcards.
     *
     * @param subsystemOpt optional subsystem (default: any)
     * @param componentOpt optional component (default: any)
     * @param nameOpt      optional alarm name (default: any)
     * @return the alarm key
     */
    static AlarmKey create(Optional<String> subsystemOpt, Optional<String> componentOpt, Optional<String> nameOpt) {
        return JAlarmService.JAlarmKeySup$.MODULE$.create(subsystemOpt, componentOpt, nameOpt);
    }

    /**
     * Creates an alarm key from the given optional subsystem and component, using wildcards for the alarm name.
     * The key may match multiple alarms if any of the arguments are empty or contain Redis wildcards.
     *
     * @param subsystemOpt optional subsystem (default: any)
     * @param componentOpt optional component (default: any)
     * @return the alarm key
     */
    static AlarmKey create(Optional<String> subsystemOpt, Optional<String> componentOpt) {
        return JAlarmService.JAlarmKeySup$.MODULE$.create(subsystemOpt, componentOpt, Optional.empty());
    }

    /**
     * Creates an alarm key from the given optional subsystem, using wildcards for the component and name.
     * The key may match multiple alarms if any of the arguments are empty or contain Redis wildcards.
     *
     * @param subsystemOpt optional subsystem (default: any)
     * @return the alarm key
     */
    static AlarmKey create(Optional<String> subsystemOpt) {
        return JAlarmService.JAlarmKeySup$.MODULE$.create(subsystemOpt, Optional.empty(), Optional.empty());
    }

    /**
     * Creates an alarm key using wildcards for the subsystem, component and name.
     * This key will match all alarms.
     *
     * @return the alarm key
     */
    static AlarmKey create() {
        return JAlarmService.JAlarmKeySup$.MODULE$.create( Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Creates an AlarmKey from the string representation.
     *
     * @param key one of the string values for key (key,severityKey, stateKey)
     * @return the AlarmKey instance
     */
    static AlarmKey create(String key) {
        return AlarmKey$.MODULE$.apply(key);
    }

    /**
     * Creates an AlarmKey for the given subsystem, component and alarm name
     *
     * @param subsystem the subsystem for the component
     * @param component the component for the alarm
     * @param name the alarm name
     * @return the AlarmKey instance
     */
    static AlarmKey create(String subsystem, String component, String name) {
        return new AlarmKey(subsystem, component, name);
    }
}
