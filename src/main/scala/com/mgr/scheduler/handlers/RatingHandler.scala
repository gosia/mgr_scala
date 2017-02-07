package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.ViewResult
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

  def delete(ratingId: String): Future[Unit] = {
    log.info(s"Deleting rating $ratingId")

    couchClient.get[docs.Rating](docs.Rating.getCouchId(ratingId)) flatMap {
      case None => throw scheduler.ValidationException(s"Rating $ratingId nie istnieje")
      case Some(doc) => couchClient.delete[docs.Rating](doc) map { _ => () }
    }
  }

  def get(ratingId: String): Future[scheduler.Rating] = {
    log.info(s"Getting rating $ratingId")
    couchClient.get[docs.Rating](docs.Rating.getCouchId(ratingId)) map {
      case None => throw scheduler.ValidationException(s"Rating $ratingId nie istnieje")
      case Some(doc) => doc.toThrift
    }
  }

  def list(): Future[Seq[scheduler.Rating]] = {
    log.info(s"Listing ratings")

    val query = couchClient
      .view("utils/by_type")
      .startkey("rating")
      .endkey("rating")
      .includeDocs

    query.execute map { result: ViewResult =>
      result.mapDocs[docs.Rating, scheduler.Rating] { _.toThrift }
    }
  }

  def save(rating: scheduler.Rating): Future[Unit] = {
    log.info(s"Saving rating ${rating.id}")

    val newDoc: docs.Rating = docs.Rating(rating)

    couchClient.get[docs.Rating](docs.Rating.getCouchId(rating.id)) flatMap {
      case None => couchClient.update[docs.Rating](newDoc) map { _ => ()}
      case Some(doc) =>
        couchClient.update[docs.Rating](newDoc.copy(_rev = doc._rev)) map { _ => ()}
    }

  }

  def countRatingHelperDoc(
    taskId: String,
    timetable: Seq[docs.GroupRoomTerm],
    groupsMap: Map[String, docs.Group],
    teachersMap: Map[String, docs.Teacher],
    roomsMap: Map[String, docs.Room],
    termsMap: Map[String, docs.Term],
    labelsMap: Map[String, docs.Label]
  ) = {

    val groupToTimetable: Map[String, Seq[docs.GroupRoomTerm]] = timetable.groupBy(_.group)

    val startParity: Map[String, Boolean] = groupToTimetable.map({
      case (groupId, groupTimetable) =>
        val terms: Seq[docs.Term] = groupTimetable.map({ x => termsMap(x.term)})
        val minTime: docs.Time = terms.minBy({ x => x.start })(docs.TimeOrdering).start
        (groupId, minTime.hour % 2 == 0)
    })

    val startEvenGroups: Seq[String] = startParity
      .filter({ case (groupId, isEven) => isEven }).keys.toSeq
    val startOddGroups: Seq[String] = startParity
      .filterNot({ case (groupId, isEven) => isEven }).keys.toSeq

    val emptyChairGroups: Map[Int, Seq[String]] = groupToTimetable.map({
      case (groupId, groupTimetable) =>
        val rooms: Seq[docs.Room] = groupTimetable.map({ x => roomsMap(x.room)})
        val maxCapacity: Int = rooms.maxBy({ x => x.capacity }).capacity
        (groupId, maxCapacity - groupsMap(groupId).students_num)
    }).toSeq.groupBy(_._2).mapValues({ xs => xs.map(_._1) })

    val hourInWorkTeachers: Map[String, Map[Int, Int]] = timetable
      .foldLeft(Map[String, Map[Int, Set[docs.Term]]]()) { case (m, grt) =>

        val teachers = groupsMap(grt.group).teachers
        val term = termsMap(grt.term)

        teachers.foldLeft(m) { case (mm, teacher) =>
          val teacherMap = mm.getOrElse(teacher, Map[Int, Set[docs.Term]]())
          val newDayTerms: Set[docs.Term] = teacherMap.getOrElse(
            scheduler.Day.valueOf(term.day).get.value, Set()
          ) + term
          val newTeacherMap = teacherMap +
            (scheduler.Day.valueOf(term.day).get.value -> newDayTerms)

          mm + (teacher -> newTeacherMap)
        }

      }
      .mapValues({ m => m.mapValues({ s =>
        val minTime: docs.Time = s.minBy(x => x.start)(docs.TimeOrdering).start
        val maxTime: docs.Time = s.maxBy(x => x.end)(docs.TimeOrdering).end
        maxTime minus minTime
      })})

     docs.TaskRatingHelper(
      _id=docs.TaskRatingHelper.getCouchId(taskId),
      _rev=None,
      term_rating_helper = docs.TermRatingHelper(
        start_even_groups = startEvenGroups,
        start_odd_groups = startOddGroups
      ),
      room_rating_helper = docs.RoomRatingHelper(
        emptyChairGroups.map({ case (k, v) => (k.toString, v)})
      ),
      teacher_rating_helper = docs.TeacherRatingHelper(
        hourInWorkTeachers.map({
          case (k1, v1) => (k1, v1.map({ case (k2, v2) => (k2.toString, v2) }))
        })
      )
    )
  }

  def countRatingHelper(taskId: String): Future[Unit] = {
    log.info(s"RATING: Counting for $taskId")

    couchClient.get[docs.Task](taskId) flatMap {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(task) =>

        val configId = task.config_id
        val timetable: Seq[docs.GroupRoomTerm] = task.timetable getOrElse Seq()

        ConfigHandler.getConfigDefMap(
          configId
        ) flatMap { case (groupsMap, teachersMap, roomsMap, termsMap, labelsMap) =>

          val newDoc = countRatingHelperDoc(
            taskId, timetable, groupsMap, teachersMap, roomsMap, termsMap, labelsMap
          )

          couchClient.get[docs.TaskRatingHelper](docs.TaskRatingHelper.getCouchId(taskId)) flatMap {
            case None => couchClient.update[docs.TaskRatingHelper](newDoc) map { _ => ()}
            case Some(doc) =>
              couchClient.update[docs.TaskRatingHelper](newDoc.copy(_rev = doc._rev)) map { _ => ()}
          }

        }

    }
  }

  def getRatingHelper(taskId: String): Future[scheduler.TaskRatingHelper] = {
    log.info(s"RATING: Get helper for $taskId")
    couchClient.get[docs.TaskRatingHelper](docs.TaskRatingHelper.getCouchId(taskId)) map {
      case None => throw scheduler.ValidationException(s"Rating helper dla $taskId nie istnieje")
      case Some(doc) => doc.toThrift
    }
  }
}
