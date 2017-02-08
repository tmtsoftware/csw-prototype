package csw.services.ccs

import com.typesafe.scalalogging.LazyLogging
import csw.util.config.Configurations.{ObserveConfig, SequenceConfig, SetupConfig, WaitConfig}
import csw.util.config.RunId
import spray.json._

/**
 * TMT Source Code: 12/15/16.
 */
object CommandsJSON extends DefaultJsonProtocol with LazyLogging {

  import Validation._

  private val missingKeyIssueType = classOf[MissingKeyIssue].getSimpleName
  private val wrongConfigKeyIssueType = classOf[WrongConfigKeyIssue].getSimpleName
  private val wrongItemTypeIssueType = classOf[WrongItemTypeIssue].getSimpleName
  private val wrongUnitsIssueType = classOf[WrongUnitsIssue].getSimpleName
  private val wrongNumberOfItemsIssueType = classOf[WrongNumberOfItemsIssue].getSimpleName
  private val singleConfigOnlyIssueType = classOf[SingleConfigOnlyIssue].getSimpleName
  private val assemblyBusyIssueType = classOf[AssemblyBusyIssue].getSimpleName
  private val unresolvedLocationsIssueType = classOf[UnresolvedLocationsIssue].getSimpleName
  private val itemValueOutOfRangeIssueType = classOf[ItemValueOutOfRangeIssue].getSimpleName
  private val wrongInternalStateIssueType = classOf[WrongInternalStateIssue].getSimpleName
  private val unsupportedCommandInStateIssueType = classOf[UnsupportedCommandInStateIssue].getSimpleName
  private val unsupportedCommandIssueType = classOf[UnsupportedCommandIssue].getSimpleName
  private val requiredServiceUnavailableIssueType = classOf[RequiredServiceUnavailableIssue].getSimpleName
  private val requiredHCDUnavailableIssueType = classOf[RequiredHCDUnavailableIssue].getSimpleName
  private val requiredAssemblyUnavailableIssueType = classOf[RequiredAssemblyUnavailableIssue].getSimpleName
  private val requiredSequencerUnavailableIssueType = classOf[RequiredSequencerUnavailableIssue].getSimpleName
  private val otherIssueType = classOf[OtherIssue].getSimpleName

  private val statusTypeKey = "statusType"
  private val issueKey = "issue"
  private val messageKey = "message"
  private val reasonKey = "reason"
  private val resultKey = "result"
  private val statusKey = "status"
  private val runIdKey = "runId"
  private val configKey = "config"
  private val overallKey = "overall"
  private val resultsKey = "results"

  private def vtoObj(issueType: String, reason: String) = JsObject(issueKey -> JsString(issueType), reasonKey -> JsString(reason))

  private def issueToType(obj: ValidationIssue) = obj match {
    case i: Validation.MissingKeyIssue                   => missingKeyIssueType
    case i: Validation.WrongConfigKeyIssue               => wrongConfigKeyIssueType
    case i: Validation.WrongItemTypeIssue                => wrongItemTypeIssueType
    case i: Validation.WrongUnitsIssue                   => wrongUnitsIssueType
    case i: Validation.WrongNumberOfItemsIssue           => wrongNumberOfItemsIssueType
    case i: Validation.SingleConfigOnlyIssue             => singleConfigOnlyIssueType
    case i: Validation.AssemblyBusyIssue                 => assemblyBusyIssueType
    case i: Validation.UnresolvedLocationsIssue          => unresolvedLocationsIssueType
    case i: Validation.ItemValueOutOfRangeIssue          => itemValueOutOfRangeIssueType
    case i: Validation.WrongInternalStateIssue           => wrongInternalStateIssueType
    case i: Validation.UnsupportedCommandInStateIssue    => unsupportedCommandInStateIssueType
    case i: Validation.UnsupportedCommandIssue           => unsupportedCommandIssueType
    case i: Validation.RequiredServiceUnavailableIssue   => requiredServiceUnavailableIssueType
    case i: Validation.RequiredHCDUnavailableIssue       => requiredHCDUnavailableIssueType
    case i: Validation.RequiredAssemblyUnavailableIssue  => requiredAssemblyUnavailableIssueType
    case i: Validation.RequiredSequencerUnavailableIssue => requiredSequencerUnavailableIssueType
    case i: Validation.OtherIssue                        => otherIssueType
  }

  implicit def validationIssueJsonFormat: JsonFormat[ValidationIssue] = new JsonFormat[ValidationIssue] {
    override def read(json: JsValue): ValidationIssue = json.asJsObject.getFields("issue", "reason") match {
      case Seq(JsString(issue), JsString(reason)) =>
        issue match {
          case `missingKeyIssueType`                   => MissingKeyIssue(reason)
          case `wrongConfigKeyIssueType`               => WrongConfigKeyIssue(reason)
          case `wrongItemTypeIssueType`                => WrongItemTypeIssue(reason)
          case `wrongUnitsIssueType`                   => WrongUnitsIssue(reason)
          case `wrongNumberOfItemsIssueType`           => WrongNumberOfItemsIssue(reason)
          case `singleConfigOnlyIssueType`             => SingleConfigOnlyIssue(reason)
          case `assemblyBusyIssueType`                 => AssemblyBusyIssue(reason)
          case `unresolvedLocationsIssueType`          => UnresolvedLocationsIssue(reason)
          case `itemValueOutOfRangeIssueType`          => ItemValueOutOfRangeIssue(reason)
          case `wrongInternalStateIssueType`           => WrongInternalStateIssue(reason)
          case `unsupportedCommandInStateIssueType`    => UnsupportedCommandInStateIssue(reason)
          case `unsupportedCommandIssueType`           => UnsupportedCommandIssue(reason)
          case `requiredServiceUnavailableIssueType`   => RequiredServiceUnavailableIssue(reason)
          case `requiredHCDUnavailableIssueType`       => RequiredHCDUnavailableIssue(reason)
          case `requiredAssemblyUnavailableIssueType`  => RequiredAssemblyUnavailableIssue(reason)
          case `requiredSequencerUnavailableIssueType` => RequiredSequencerUnavailableIssue(reason)
          case `otherIssueType`                        => OtherIssue(reason)
        }
    }

    override def write(obj: ValidationIssue): JsValue = vtoObj(issueToType(obj), obj.reason)
  }

  import CommandStatus._

  private val acceptedOCSType = CommandStatus.Accepted.toString
  private val notAcceptedOCSType = CommandStatus.NotAccepted.toString
  private val incompleteOCSType = CommandStatus.Incomplete.toString
  private val allCompletedOCSType = CommandStatus.AllCompleted.toString

  private def overallTypeToOverall(overallIn: String): OverallCommandStatus = overallIn match {
    case `acceptedOCSType`     => CommandStatus.Accepted
    case `notAcceptedOCSType`  => CommandStatus.NotAccepted
    case `incompleteOCSType`   => CommandStatus.Incomplete
    case `allCompletedOCSType` => CommandStatus.AllCompleted
  }

  private val invalidCSType = classOf[CommandStatus.Invalid].getSimpleName
  private val validCSType = CommandStatus.Valid.toString
  private val noLongerValidCSType = classOf[CommandStatus.NoLongerValid].getSimpleName
  private val completedCSType = CommandStatus.Completed.toString
  private val completedWithResultCSType = classOf[CompletedWithResult].getSimpleName
  private val inProgressCSType = classOf[CommandStatus.InProgress].getSimpleName
  private val errorCSType = classOf[CommandStatus.Error].getSimpleName
  private val abortedCSType = CommandStatus.Aborted.toString
  private val cancelledCSType = CommandStatus.Cancelled.toString

  import csw.util.config.ConfigJSON._

  private def getMessage(json: JsValue): String = {
    json.asJsObject.getFields(messageKey) match {
      case Seq(JsString(message)) => message
    }
  }

  private def getConfig(json: JsValue): SetupConfig = {
    json.asJsObject.getFields(resultKey) match {
      case Seq(sc) => readConfig(sc)
    }
  }

  private def csWithIssue(statusType: String, issue: ValidationIssue) = JsObject(statusTypeKey -> JsString(statusType), issueKey -> JsString(issueToType(issue)), reasonKey -> JsString(issue.reason))

  private def csWithMessage(statusType: String, message: String) = JsObject(statusTypeKey -> JsString(statusType), messageKey -> JsString(message))

  private def csWithConfig(statusType: String, sc: SetupConfig) = JsObject(statusTypeKey -> JsString(statusType), resultKey -> writeConfig(sc))

  private def csOnly(statusType: String) = JsObject(statusTypeKey -> JsString(statusType))

  implicit def CommandStatusJsonFormat: JsonFormat[CommandStatus] = new JsonFormat[CommandStatus] {
    override def read(json: JsValue): CommandStatus = {
      json.asJsObject.getFields(statusTypeKey) match {
        case Seq(JsString(statusType)) => statusType match {
          case `invalidCSType`             => CommandStatus.Invalid(json.convertTo[ValidationIssue])
          case `validCSType`               => CommandStatus.Valid
          case `noLongerValidCSType`       => CommandStatus.NoLongerValid(json.convertTo[ValidationIssue])
          case `completedCSType`           => CommandStatus.Completed
          case `inProgressCSType`          => CommandStatus.InProgress(getMessage(json))
          case `errorCSType`               => CommandStatus.Error(getMessage(json))
          case `completedWithResultCSType` => CompletedWithResult(getConfig(json))
          case `abortedCSType`             => CommandStatus.Aborted
          case `cancelledCSType`           => CommandStatus.Cancelled
        }
      }
    }

    override def write(obj: CommandStatus): JsValue = obj match {
      case CommandStatus.Invalid(vi)                 => csWithIssue(invalidCSType, vi)
      case CommandStatus.Valid                       => csOnly(validCSType)
      case CommandStatus.NoLongerValid(vi)           => csWithIssue(noLongerValidCSType, vi)
      case CommandStatus.Completed                   => csOnly(completedCSType)
      case CommandStatus.InProgress(message)         => csWithMessage(inProgressCSType, message)
      case CommandStatus.Error(message)              => csWithMessage(errorCSType, message)
      case CommandStatus.CompletedWithResult(result) => csWithConfig(completedWithResultCSType, result)
      case CommandStatus.Aborted                     => csOnly(abortedCSType)
      case CommandStatus.Cancelled                   => csOnly(cancelledCSType)
    }
  }

  implicit def SequenceConfigJsonFormat: JsonFormat[SequenceConfig] = new JsonFormat[SequenceConfig] {
    override def read(json: JsValue): SequenceConfig = readConfig(json)

    override def write(obj: SequenceConfig): JsValue = {
      obj match {
        case sc: SetupConfig   => writeConfig(sc)
        case oc: ObserveConfig => writeConfig(oc)
        case wc: WaitConfig    => writeConfig(wc)
      }
    }
  }

  implicit def CommandResultPairJsonFormat: JsonFormat[CommandResultPair] = new JsonFormat[CommandResultPair] {
    override def read(json: JsValue): CommandResultPair = json.asJsObject.getFields(statusKey, configKey) match {
      case Seq(status, config) =>
        val cs = status.convertTo[CommandStatus]
        val cconfig = config.convertTo[SequenceConfig]
        CommandResultPair(cs, cconfig)
    }

    override def write(obj: CommandResultPair): JsObject =
      JsObject(
        statusKey -> obj.status.toJson,
        configKey -> obj.config.toJson
      )
  }

  implicit def CommandResultJsonFormat: RootJsonFormat[CommandResult] = new RootJsonFormat[CommandResult] {
    override def read(json: JsValue): CommandResult = json.asJsObject.getFields(runIdKey, overallKey, resultsKey) match {
      case Seq(JsString(runId), JsString(overall), results) =>
        val runIdOut = RunId(runId)
        val overallOut = overallTypeToOverall(overall)
        val resultsOut = CommandResults(results.convertTo[List[CommandResultPair]])
        CommandResult(runIdOut, overallOut, resultsOut)
    }

    override def write(obj: CommandResult): JsValue = JsObject(
      runIdKey -> JsString(obj.runId.id),
      overallKey -> JsString(obj.overall.toString),
      resultsKey -> JsArray(obj.details.results.map(_.toJson): _*)
    )
  }

}
