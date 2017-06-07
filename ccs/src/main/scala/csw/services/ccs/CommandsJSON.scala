package csw.services.ccs

import com.typesafe.scalalogging.LazyLogging
import csw.util.param.Parameters.{Observe, SequenceCommand, Setup, Wait}
import spray.json._

/**
 * TMT Source Code: 12/15/16.
 */
object CommandsJSON extends DefaultJsonProtocol with LazyLogging {

  import Validation._

  private val missingKeyIssueType = classOf[MissingKeyIssue].getSimpleName
  private val wrongPrefixIssueType = classOf[WrongPrefixIssue].getSimpleName
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
  //  private val statusKey = "status"
  //  private val runIdKey = "runId"
  //  private val configKey = "config"
  //  private val overallKey = "overall"
  //  private val resultsKey = "results"

  private def vtoObj(issueType: String, reason: String) = JsObject(issueKey -> JsString(issueType), reasonKey -> JsString(reason))

  private def issueToType(obj: ValidationIssue) = obj match {
    case _: Validation.MissingKeyIssue                   => missingKeyIssueType
    case _: Validation.WrongPrefixIssue               => wrongPrefixIssueType
    case _: Validation.WrongItemTypeIssue                => wrongItemTypeIssueType
    case _: Validation.WrongUnitsIssue                   => wrongUnitsIssueType
    case _: Validation.WrongNumberOfItemsIssue           => wrongNumberOfItemsIssueType
    case _: Validation.SingleConfigOnlyIssue             => singleConfigOnlyIssueType
    case _: Validation.AssemblyBusyIssue                 => assemblyBusyIssueType
    case _: Validation.UnresolvedLocationsIssue          => unresolvedLocationsIssueType
    case _: Validation.ItemValueOutOfRangeIssue          => itemValueOutOfRangeIssueType
    case _: Validation.WrongInternalStateIssue           => wrongInternalStateIssueType
    case _: Validation.UnsupportedCommandInStateIssue    => unsupportedCommandInStateIssueType
    case _: Validation.UnsupportedCommandIssue           => unsupportedCommandIssueType
    case _: Validation.RequiredServiceUnavailableIssue   => requiredServiceUnavailableIssueType
    case _: Validation.RequiredHCDUnavailableIssue       => requiredHCDUnavailableIssueType
    case _: Validation.RequiredAssemblyUnavailableIssue  => requiredAssemblyUnavailableIssueType
    case _: Validation.RequiredSequencerUnavailableIssue => requiredSequencerUnavailableIssueType
    case _: Validation.OtherIssue                        => otherIssueType
  }

  implicit def validationIssueJsonFormat: JsonFormat[ValidationIssue] = new JsonFormat[ValidationIssue] {
    override def read(json: JsValue): ValidationIssue = json.asJsObject.getFields("issue", "reason") match {
      case Seq(JsString(issue), JsString(reason)) =>
        issue match {
          case `missingKeyIssueType`                   => MissingKeyIssue(reason)
          case `wrongPrefixIssueType`               => WrongPrefixIssue(reason)
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

  private val invalidCSType = classOf[CommandStatus.Invalid].getSimpleName
  private val acceptedCSType = CommandStatus.Accepted.toString
  private val noLongerValidCSType = classOf[CommandStatus.NoLongerValid].getSimpleName
  private val completedCSType = CommandStatus.Completed.toString
  private val inProgressCSType = classOf[CommandStatus.InProgress].getSimpleName
  private val errorCSType = classOf[CommandStatus.Error].getSimpleName
  private val abortedCSType = CommandStatus.Aborted.toString
  private val cancelledCSType = CommandStatus.Cancelled.toString

  import csw.util.param.ItemSetJson._

  private def getMessage(json: JsValue): String = {
    json.asJsObject.getFields(messageKey) match {
      case Seq(JsString(message)) => message
    }
  }

  private def getItemSet(json: JsValue): Setup = {
    json.asJsObject.getFields(resultKey) match {
      case Seq(sc) => readSequenceItemSet(sc)
    }
  }

  private def csWithIssue(statusType: String, issue: ValidationIssue) = JsObject(statusTypeKey -> JsString(statusType), issueKey -> JsString(issueToType(issue)), reasonKey -> JsString(issue.reason))

  private def csWithMessage(statusType: String, message: String) = JsObject(statusTypeKey -> JsString(statusType), messageKey -> JsString(message))

  private def csWithConfig(statusType: String, sc: Setup) = JsObject(statusTypeKey -> JsString(statusType), resultKey -> writeSequenceItemSet(sc))

  private def csOnly(statusType: String) = JsObject(statusTypeKey -> JsString(statusType))

  implicit def CommandStatusJsonFormat: JsonFormat[CommandResponse] = new JsonFormat[CommandResponse] {
    override def read(json: JsValue): CommandResponse = {
      json.asJsObject.getFields(statusTypeKey) match {
        case Seq(JsString(statusType)) => statusType match {
          case `invalidCSType`       => CommandStatus.Invalid(json.convertTo[ValidationIssue])
          case `acceptedCSType`      => CommandStatus.Accepted
          case `noLongerValidCSType` => CommandStatus.NoLongerValid(json.convertTo[ValidationIssue])
          case `completedCSType`     => CommandStatus.Completed
          case `inProgressCSType`    => CommandStatus.InProgress(getMessage(json))
          case `errorCSType`         => CommandStatus.Error(getMessage(json))
          //          case `completedWithResultCSType` => CompletedWithResult(getConfig(json))
          case `abortedCSType`       => CommandStatus.Aborted
          case `cancelledCSType`     => CommandStatus.Cancelled
        }
      }
    }

    override def write(obj: CommandResponse): JsValue = obj match {
      case CommandStatus.Invalid(vi)         => csWithIssue(invalidCSType, vi)
      case CommandStatus.Accepted            => csOnly(acceptedCSType)
      case CommandStatus.NoLongerValid(vi)   => csWithIssue(noLongerValidCSType, vi)
      case CommandStatus.Completed           => csOnly(completedCSType)
      case CommandStatus.InProgress(message) => csWithMessage(inProgressCSType, message)
      case CommandStatus.Error(message)      => csWithMessage(errorCSType, message)
      //      case CommandStatus.CompletedWithResult(result) => csWithConfig(completedWithResultCSType, result)
      case CommandStatus.Aborted             => csOnly(abortedCSType)
      case CommandStatus.Cancelled           => csOnly(cancelledCSType)
    }
  }

  implicit def SequenceConfigJsonFormat: JsonFormat[SequenceCommand] = new JsonFormat[SequenceCommand] {
    override def read(json: JsValue): SequenceCommand = readSequenceItemSet(json)

    override def write(obj: SequenceCommand): JsValue = {
      obj match {
        case sc: Setup   => writeSequenceItemSet(sc)
        case oc: Observe => writeSequenceItemSet(oc)
        case wc: Wait    => writeSequenceItemSet(wc)
      }
    }
  }
}
