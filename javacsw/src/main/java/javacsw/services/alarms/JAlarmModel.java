package javacsw.services.alarms;

import csw.services.alarms.AlarmModel;
import csw.services.alarms.AlarmModel.SeverityLevel;

/**
 * Java API for AlarmModel
 */
public class JAlarmModel {
    /**
     * Java access to SeverityLevel values
     */
    @SuppressWarnings("unused")
    public static class JSeverityLevel {
        public static final SeverityLevel Disconnected = JAlarmService.JSeverityLevelSup$.MODULE$.Disconnected();
        public static final SeverityLevel Indeterminate = JAlarmService.JSeverityLevelSup$.MODULE$.Indeterminate();
        public static final SeverityLevel Okay = JAlarmService.JSeverityLevelSup$.MODULE$.Okay();
        public static final SeverityLevel Warning = JAlarmService.JSeverityLevelSup$.MODULE$.Warning();
        public static final SeverityLevel Major = JAlarmService.JSeverityLevelSup$.MODULE$.Major();
        public static final SeverityLevel Critical = JAlarmService.JSeverityLevelSup$.MODULE$.Critical();
    }

    /**
     * Java access to AlarmType values
     */
    @SuppressWarnings("unused")
    public static class JAlarmType {
        public static final AlarmModel.AlarmType Absolute = JAlarmService.JAlarmTypeSup$.MODULE$.Absolute();
        public static final AlarmModel.AlarmType BitPattern = JAlarmService.JAlarmTypeSup$.MODULE$.BitPattern();
        public static final AlarmModel.AlarmType Calculated = JAlarmService.JAlarmTypeSup$.MODULE$.Calculated();
        public static final AlarmModel.AlarmType Deviation = JAlarmService.JAlarmTypeSup$.MODULE$.Deviation();
        public static final AlarmModel.AlarmType Discrepancy = JAlarmService.JAlarmTypeSup$.MODULE$.Discrepancy();
        public static final AlarmModel.AlarmType Instrument = JAlarmService.JAlarmTypeSup$.MODULE$.Instrument();
        public static final AlarmModel.AlarmType RateChange = JAlarmService.JAlarmTypeSup$.MODULE$.RateChange();
        public static final AlarmModel.AlarmType RecipeDriven = JAlarmService.JAlarmTypeSup$.MODULE$.RecipeDriven();
        public static final AlarmModel.AlarmType Safety = JAlarmService.JAlarmTypeSup$.MODULE$.Safety();
        public static final AlarmModel.AlarmType Statistical = JAlarmService.JAlarmTypeSup$.MODULE$.Statistical();
        public static final AlarmModel.AlarmType System = JAlarmService.JAlarmTypeSup$.MODULE$.System();
    }

    /**
     * Java access to Health values
     */
    public static class JHealth {
        public static final AlarmModel.Health Good = JAlarmService.JHealthSup$.MODULE$.Good();
        public static final AlarmModel.Health Ill = JAlarmService.JHealthSup$.MODULE$.Ill();
        public static final AlarmModel.Health Bad = JAlarmService.JHealthSup$.MODULE$.Bad();
    }
}
