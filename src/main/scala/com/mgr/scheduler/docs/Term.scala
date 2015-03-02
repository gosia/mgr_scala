package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Time(
  day: String,
  hour: Int,
  minute: Int
)

final case class Term(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  start: Option[Time],
  end: Option[Time],

  `type`: String = Term.`type`
) extends Base {

  private def dateValid(t: Time): Boolean = {
    val dayIsValid = scheduler.Day.valueOf(t.day).isDefined
    val hourIsValid = 0 <= t.hour && t.hour <= 24
    val minuteIsValid = 0 <= t.minute && t.minute <= 60

    dayIsValid && hourIsValid && minuteIsValid
  }

  private def startBeforeEnd(start: Time, end: Time): Boolean = {
    val startDay = scheduler.Day.valueOf(start.day).get.value
    val endDay = scheduler.Day.valueOf(end.day).get.value

    startDay < endDay || (
      startDay == endDay && (
        start.hour < end.hour || (start.hour == end.hour && start.minute < end.minute)
      )
    )
  }

  def isValid: (Option[String], Boolean) = {
    (this.start, this.end) match {
      case (None, None) => (None, true)
      case (Some(_), None) => (Some(s"Term $getRealId is not valid (only start defined)"), false)
      case (None, Some(_)) => (Some(s"Term $getRealId is not valid (only end defined)"), false)
      case (Some(start), Some(end)) => dateValid(start) match {
        case false => (Some(s"Term $getRealId is not valid (start not valid)"), false)
        case true => dateValid(end) match {
          case false => (Some(s"Term $getRealId is not valid (end not valid)"), false)
          case true => startBeforeEnd(start, end) match {
            case false => (Some(s"Term $getRealId is not valid (end before start)"), false)
            case true => (None, true)
          }
        }
      }
    }
  }

}

object Term extends BaseObj {
  val `type`: String = "term"

  def apply(configId: String, term: scheduler.Term): Term = Term(
    _id = Term.getCouchId(configId, term.id),
    config_id = configId,
    start = Some(Time(
      term.startTime.day.name.toLowerCase,
      term.startTime.hour,
      term.startTime.minute
    )),
    end = Some(Time(term.endTime.day.name.toLowerCase, term.endTime.hour, term.endTime.minute))
  )
}
