package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Vote(
  course: String,
  points: Short,
  student: String
) {
  def toThrift: scheduler.StudentVote = scheduler.StudentVote(
    course = course,
    student = student,
    points = points
  )
}

object Vote {
  def apply(x: scheduler.StudentVote): Vote = Vote(x.course, x.points, x.student)
}

final case class StudentVotes(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  votes: Seq[Vote],

  `type`: String = StudentVotes.`type`
) extends Base {
  def toThrift: scheduler.StudentVotes = scheduler.StudentVotes(
    configId = config_id,
    votes = votes.map(_.toThrift),
    studentsNum = votes.map(_.student).toSet.size,
    pointsSum = votes.map(_.points).sum
  )
}

object StudentVotes extends BaseObj {
  val `type`: String = "student_votes"

  def getCouchId(configId: String): String = getCouchId(configId, "all")

  def apply(configId: String, votes: Seq[scheduler.StudentVote]): StudentVotes =
    StudentVotes(
      _id = StudentVotes.getCouchId(configId),
      config_id = configId,
      votes = votes.map(Vote.apply)
    )
}
