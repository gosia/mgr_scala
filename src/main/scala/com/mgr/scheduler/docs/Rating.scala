package com.mgr.scheduler.docs

import com.mgr.utils.couch
import com.mgr.thrift.scheduler
import com.mgr.utils.logging.Logging

final case class RatingWeights(
  term_rating: Int,
  room_rating: Int,
  teacher_rating: Int
) {
  def toThrift: scheduler.RatingWeights = scheduler.RatingWeights(
    term_rating, room_rating, teacher_rating
  )
}

object RatingWeights {
  def apply(r: scheduler.RatingWeights): RatingWeights = RatingWeights(
    r.termRating, r.roomRating, r.teacherRating
  )
}

final case class TermRating(
  terms: Map[String, Int],
  start_even: Int,
  start_odd: Int,
  terms_day_bonus: Map[String, Int],
  terms_hour_bonus: Map[String, Int]
) extends Logging {
  def toThrift: scheduler.TermRating = scheduler.TermRating(
    terms, start_even, start_odd,
    terms_day_bonus.map({ case (k, v) => (k.toInt, v)}),
    terms_hour_bonus.map({ case (k, v) => (k.toInt, v)})
  )
}

object TermRating {
  def apply(r: scheduler.TermRating): TermRating = TermRating(
    r.terms.toMap, r.startEven, r.startOdd,
    r.termsDayBonus.toMap.map({ case (k, v) => (k.toString, v)}),
    r.termsHourBonus.toMap.map({ case (k, v) => (k.toString, v)})
  )
}

final case class RoomRating(
  too_big_capacity: Map[String, Int]
) {
  def toThrift: scheduler.RoomRating = scheduler.RoomRating(
    too_big_capacity.map({ case (k, v) => (k.toInt, v)})
  )
}

object RoomRating {
  def apply(r: scheduler.RoomRating): RoomRating = RoomRating(
    r.tooBigCapacity.toMap.map({ case (k, v) => (k.toString, v)})
  )
}

final case class TeacherRating(
  total_hours_in_work: Map[String, Int],
  no_work_days_num: Map[String, Int],
  no_work_days_on_mon_fri: Int
) {
  def toThrift: scheduler.TeacherRating = scheduler.TeacherRating(
    total_hours_in_work.map({ case (k, v) => (k.toInt, v)}),
    no_work_days_num.map({ case (k, v) => (k.toInt, v)}),
    no_work_days_on_mon_fri
  )
}

object TeacherRating {
  def apply(r: scheduler.TeacherRating): TeacherRating = TeacherRating(
    r.totalHoursInWork.toMap.map({ case (k, v) => (k.toString, v)}),
    r.noWorkDaysNum.toMap.map({ case (k, v) => (k.toString, v)}),
    r.noWorkDaysOnMonFri
  )
}

final case class Rating(
  _id: String,
  _rev: Option[String] = None,

  weights: RatingWeights,
  term_rating: TermRating,
  room_rating: RoomRating,
  teacher_rating: TeacherRating,

  `type`: String = "rating"
) extends couch.Document {


  def toThrift: scheduler.Rating = scheduler.Rating(
    Rating.getIdForUser(_id), weights.toThrift,
    term_rating.toThrift, room_rating.toThrift, teacher_rating.toThrift
  )
}

object Rating extends Logging {
  def getCouchId(userDefinedId: String): String = s"rating:$userDefinedId"
  def getIdForUser(couchId: String): String = couchId.split(":").tail.mkString(":")


  def apply(r: scheduler.Rating): Rating = Rating(
    _id = getCouchId(r.id),
    weights = RatingWeights(r.weights),
    term_rating = TermRating(r.termRating),
    room_rating = RoomRating(r.roomRating),
    teacher_rating = TeacherRating(r.teacherRating)
  )
}
