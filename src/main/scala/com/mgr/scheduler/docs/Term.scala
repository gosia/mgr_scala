package com.mgr.scheduler.docs

import com.mgr.utils.couch
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

  `type`: String = "term"
) extends couch.Document {

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

  def isValid: Boolean = {
    (this.start, this.end) match {
      case (None, None) => true
      case (Some(_), None) => false
      case (None, Some(_)) => false
      case (Some(start), Some(end)) =>
        dateValid(start) && dateValid(end) && startBeforeEnd(start, end)
    }
  }

}

object Term {
  def apply(configId: String, term: scheduler.Term): Term = Term(
    _id = Base.getCouchId(configId, term.id),
    config_id = configId,
    start = Some(Time(term.start.day.name.toLowerCase, term.start.hour, term.start.minute)),
    end = Some(Time(term.end.day.name.toLowerCase, term.end.hour, term.end.minute))
  )
}
