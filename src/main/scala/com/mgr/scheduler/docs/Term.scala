package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Time(
  hour: Int,
  minute: Int
) {
  def asThrift: scheduler.Time = scheduler.Time(
    this.hour.toShort, this.minute.toShort
  )

  def isTheSame(other: Time): Boolean = this.hour == other.hour && this.minute == other.minute

  def minus(other:Time): Int = {
    hour * 60 + minute - (other.hour * 60 + other.minute)
  }
}

object Time {
  def apply(time: scheduler.Time): Time = Time(time.hour, time.minute)
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


  def editConfig(newConfigId: String): Term = Term.apply(
    newConfigId, this.asThrift
  )

  def isTheSame(other: Term): Boolean = {
    this.start.isTheSame(other.start) && this.end.isTheSame(other.end) &&
      this.day == other.day && this.config_id == other.config_id && this._id == other._id
  }

  def contains(point: scheduler.Point): Boolean = {
    val time = Time.apply(point.time)

    this.day == point.day.name.toLowerCase &&
      TimeOrdering.compare(this.start, time) == -1 &&
      TimeOrdering.compare(time, this.end) == -1
  }

}

object Term extends BaseObj {
  val `type`: String = "term"

  def apply(configId: String, term: scheduler.Term): Term = Term(
    _id = Term.getCouchId(configId, term.id),
    config_id = configId,
    start = Time.apply(term.startTime),
    end = Time.apply(term.endTime),
    day = term.day.name.toLowerCase
  )

  def forIi(configId: String, day: Int, hour: Int): Term = Term(
    _id = Term.getCouchId(configId, (day * 100 + hour).toString),
    config_id = configId,
    start = Time(hour, 15),
    end = Time(hour + 1, 0),
    day = scheduler.Day(day).name.toLowerCase
  )

  def findByPoint(terms: Seq[Term], point: scheduler.Point): Option[Term] = {
    terms.foldLeft[Option[Term]](None) { case (r, t) => r match {
      case None => if (t.contains(point)) { Some(t) } else None
      case _ => r
    }}
  }

  def findManyByPoint(terms: Seq[Term], point: scheduler.Point, howMany: Int): Option[Seq[Term]] = {
    val first: Option[Seq[Term]] = Term.findByPoint(terms, point) map { t => Seq(t) }
    (2 to howMany).foldLeft[Option[Seq[Term]]](first) { case (r, _) => r match {
      case None => None
      case Some(tx) =>
        val newLast: Option[Term] = tx.last.findNext(terms)
        newLast match {
          case None => None
          case Some(t) => Some(tx ++ Seq(t))
        }
    }}
  }

  def countConflicts(terms: Seq[Term]): Int = {
    terms.zipWithIndex.map { case (term1, i1) =>
      terms.zipWithIndex map { case (term2, i2) =>
        i1 < i2 match {
          case true => if (term1.compare(term2) == 0) 1 else 0
          case false => 0
        }
      } sum
    } sum
  }

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
