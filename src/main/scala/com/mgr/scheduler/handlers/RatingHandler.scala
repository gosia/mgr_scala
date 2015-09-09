package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.utils.logging.Logging

object RatingHandler extends Logging  with Couch {


  def getStudentRating(
    student: String,
    timetable: Seq[docs.GroupRoomTerm],
    groupsMap: Map[String, docs.Group],
    termsMap: Map[String, docs.Term],
    votes: Map[String, Short]
  ): Future[Short] = {
    val userCourses = votes.keys.toSet
    val userTimetable = timetable.filter(t => userCourses.contains(groupsMap(t.group).extra.course))

    val vector: Seq[Seq[docs.Term]] = userTimetable.foldLeft(Map[String, Set[docs.Term]]())({
      (userTimetableMap, t) =>
        val group = groupsMap(t.group)
        val key = "${group.extra.course}:${group.extra.group_type}"

        userTimetableMap + (key -> (userTimetableMap.getOrElse(key, Set()) + termsMap(t.term)))
    }).mapValues(_.toSeq).values.toSeq

    val possibilityNum = vector.foldLeft(1)({ (x, seq) => x * seq.size}).toShort
    log.info(s"RATING: Possibilities for student $student - $possibilityNum")

    val indices = Seq.fill(vector.size)(0)

    def nextStep(indices: Seq[Int], i: Int): Option[(Seq[Int], Int)] = {
      indices(i) + 1 < vector(i).size match {
        case true => Some((indices.updated(i, indices(i) + 1), i))
        case false =>
          val zeroIndices = (0 to i).foldLeft[Seq[Int]](indices) {case (newIndices, ii) =>
            newIndices.updated(ii, 0)
          }
          val result = (i + 1 to vector.size - 1).foldLeft[(Seq[Int], Int, Boolean)](
            zeroIndices, -1, false
          ) {
            case (x, ii) =>
              x._3 match {
                case true => x
                case false =>
                  x._1(ii) + 1 < vector(ii).size match {
                    case true => (x._1.updated(ii, x._1(ii) + 1), ii, true)
                    case false => x
                  }
              }
          }
          result._3 match {
            case true => Some((result._1, result._2))
            case false => None
          }
      }
    }

    val minConflicts = (1 to possibilityNum).foldLeft[(Option[(Seq[Int], Int)], Int)](
        (Some(indices, 0), Int.MaxValue)
      ) { case (x, _) =>
      x._1 match {
        case None => x
        case Some((curIndices, cur)) =>
          val terms: Seq[docs.Term] = curIndices.zipWithIndex.map({case (num, i) => vector(i)(num)})
          val conflicts = docs.Term.countConflicts(terms)
          val next = nextStep(curIndices, cur)

          (next, if(conflicts < x._2) conflicts else x._2)
      }
    }

    log.info(s"RATING: conflicts $minConflicts")

    Future.value(minConflicts._2.toShort)
  }

  def getRating(taskId: String): Future[Short] = {
    log.info(s"RATING: Get for $taskId")

    couchClient.get[docs.Task](taskId) flatMap {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(task) =>

        val configId = task.config_id
        val timetable: Seq[docs.GroupRoomTerm] = task.timetable getOrElse Seq()

        timetable.size match {
          case 0 => Future.value(0)
          case _ =>
            ConfigHandler.getConfigDefMap(
              configId
            ) flatMap { case (groupsMap, teachersMap, roomsMap, termsMap, labelsMap) =>

              VoteHandler.get(configId) flatMap { userVotes: scheduler.UsersVotes =>

                val studentRatings: Seq[Future[Short]] = userVotes.votes.keys.toSeq map {
                  student => getStudentRating(
                    student,
                    timetable,
                    groupsMap,
                    termsMap,
                    userVotes.votes(student).asInstanceOf[Map[String, Short]]
                  )
                }

                Future.collect(studentRatings) flatMap { ratings =>
                  val studentRating = ratings.sum

                  Future.value(studentRating)
                }
              }
            }
        }
    }
  }

}
