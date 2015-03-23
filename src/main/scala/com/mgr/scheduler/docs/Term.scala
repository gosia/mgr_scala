package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Time(
  hour: Int,
  minute: Int
) {
  def asThrift = scheduler.Time(
    this.hour.toShort, this.minute.toShort
  )
}

final case class Term(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  day: String,

  start: Time,
  end: Time,

  `type`: String = Term.`type`
) extends Base with Ordered[Term] {

  private def dateValid(t: Time): Boolean = {
    val hourIsValid = 0 <= t.hour && t.hour <= 24
    val minuteIsValid = 0 <= t.minute && t.minute <= 60

    hourIsValid && minuteIsValid
  }

  private def startBeforeEnd(start: Time, end: Time): Boolean = {
      start.hour < end.hour || (start.hour == end.hour && start.minute < end.minute)
  }

  def isValid: (Option[String], Boolean) = {
    dateValid(start) match {
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

  def toTxt: String = {
    f"$day ${start.hour}%02d:${start.minute}%02d-${end.hour}%02d:${end.minute}%02d"
  }

  def compare(that: Term): Int = {
    val dayDiff = scheduler.Day.valueOf(day).get.value - scheduler.Day.valueOf(that.day).get.value
    if (dayDiff == 0) {
      TimeOrdering.compare(end, that.start)
    } else {
      dayDiff
    }
  }

  def isNext(that: Term): Boolean = {
    val maxMinuteDiff = 30 // TODO(gosia): move to config

    val dayDiff = scheduler.Day.valueOf(day).get.value - scheduler.Day.valueOf(that.day).get.value

    val t1 = end.hour * 60 + end.minute
    val t2 = that.start.hour * 60 + that.start.minute
    val minuteDiff =  t2 - t1

    dayDiff == 0 && minuteDiff > 0 && minuteDiff < maxMinuteDiff
  }

  def findNext(terms: Seq[Term]): Option[Term] = {
    val next = terms.foldLeft[Option[Term]](None) { case (result, t) => {
      result match {
        case Some(_) => result
        case None => isNext(t) match {
          case true => Some(t)
          case false => None
        }
      }
    }}
    next
  }

  def asThrift: scheduler.Term = scheduler.Term(
    this.getRealId,
    this.start.asThrift,
    this.end.asThrift,
    scheduler.Day.valueOf(this.day).get
  )

}

object Term extends BaseObj {
  val `type`: String = "term"

  def apply(configId: String, term: scheduler.Term): Term = Term(
    _id = Term.getCouchId(configId, term.id),
    config_id = configId,
    start = Time(
      term.startTime.hour,
      term.startTime.minute
    ),
    end = Time(term.endTime.hour, term.endTime.minute),
    day = term.day.name.toLowerCase
  )

}

object TimeOrdering extends Ordering[Time] {
  def compare(a: Time, b: Time): Int = {

    (a.hour - b.hour, a.minute - b.minute) match {
      case (h, m) if h < 0 => -1
      case (0, m) if m < 0 => -1
      case (0, 0) => 0
      case _ => 1
    }
  }
}
