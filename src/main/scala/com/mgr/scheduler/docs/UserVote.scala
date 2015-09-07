package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler
import com.mgr.utils.couch

final case class CourseVote(
  course: String,
  vote: Short
)

object CourseVote {
  def toThrift(votes: Seq[CourseVote]): Map[String, Short] = {
    votes map { x => (x.course, x.vote) } toMap
  }
}

final case class UserVote(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,
  user: String,
  votes_num: Short,

  votes: Seq[CourseVote],

  `type`: String = UserVote.`type`
) extends Base

object UserVote extends BaseObj {
  val `type`: String = "user_vote"

  def apply(configId: String, votes: Map[String, Map[String, Short]], user: String): UserVote = {
    val userVotes: Seq[CourseVote] = votes
      .getOrElse(user, Map[String, Short]())
      .asInstanceOf[Map[String, Short]]
      .toSeq
      .map({ x => CourseVote(x._1, x._2) })

    UserVote(
      _id = UserVote.getCouchId(configId, ""),
      config_id = configId,
      user = user,
      votes_num = userVotes.size.toShort,
      votes = userVotes
    )
  }

  def toThrift(configId: String, docs: Seq[UserVote]): scheduler.UsersVotes = scheduler.UsersVotes(
    configId,
    docs.map(x => x.votes_num).sum,
    docs.map(x => (x.user, CourseVote.toThrift(x.votes))).toMap
  )
}
