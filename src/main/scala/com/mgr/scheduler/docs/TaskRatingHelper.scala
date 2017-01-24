package com.mgr.scheduler.docs

import com.mgr.utils.couch
import com.mgr.thrift.scheduler


final case class TermRatingHelper(
  start_even_groups: Seq[String],
  start_odd_groups: Seq[String]
) {
  def toThrift: scheduler.TermRatingHelper = scheduler.TermRatingHelper(
    start_even_groups.map(Group.getRealId), start_odd_groups.map(Group.getRealId)
  )
}

final case class RoomRatingHelper(
  empty_chair_groups: Map[String, Seq[String]] // empty chairs -> groups
) {
  def toThrift: scheduler.RoomRatingHelper = scheduler.RoomRatingHelper(
    empty_chair_groups.mapValues(_.map(Group.getRealId))
  )
}

final case class TeacherRatingHelper(
  hours_in_work: Map[String, Map[String, Int]] // teacher -> day -> hours in work
) {
  def toThrift: scheduler.TeacherRatingHelper = scheduler.TeacherRatingHelper(
    hours_in_work.map({case (k1, v1) => (Teacher.getRealId(k1), v1)})
  )
}

final case class TaskRatingHelper(
  _id: String,
  _rev: Option[String] = None,

  term_rating_helper: TermRatingHelper,
  room_rating_helper: RoomRatingHelper,
  teacher_rating_helper: TeacherRatingHelper,

  `type`: String = "task_rating_helper"
) extends couch.Document {
  def toThrift: scheduler.TaskRatingHelper = scheduler.TaskRatingHelper(
    term_rating_helper.toThrift,
    room_rating_helper.toThrift,
    teacher_rating_helper.toThrift
  )
}

object TaskRatingHelper {
  def getCouchId(taskId: String): String = s"task_rating_helper:$taskId"
  def getIdForUser(couchId: String): String = couchId.split(":").tail.mkString(":")
}
