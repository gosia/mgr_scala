package com.mgr.scheduler.docs

import scalaz._
import Scalaz._

import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler


case class TaskRating(
  rating: Rating,
  taskRatingHelper: TaskRatingHelper,
  timetable: Seq[GroupRoomTerm],
  termsMap: Map[String, Term],
  groupsMap: Map[String, Group],
  votes: Seq[Vote],
  group: docs.Group,
  newTimes: Seq[(String, String)] // sequence of (room, term) ids
) {

  private lazy val votesByCourse = votes.groupBy(_.course)
  private lazy val votesByStudents: Map[String, Seq[Vote]] = votes.groupBy(_.student)
  private lazy val timetableByCourse: Map[String, Seq[GroupRoomTerm]] =
    timetable.groupBy({x => groupsMap(x.group).extra.course })
  private lazy val groupsNumByType: Map[String, Map[String, Int]] = timetableByCourse
    .mapValues({ xs =>
      xs.groupBy({ x => groupsMap(x.group).extra.group_type }).mapValues(_.size)
    })

  private def getSectionPoints(sections: Map[String, Int], value: Int): Int = {
    sections.size match {
      case 0 => 0
      case _ =>
        val intSections = sections.map({case (num, points) => (num.toInt, points) })
        val key = intSections.keys.filter(x => x < value).max
        intSections(key)
    }
  }

  private def getSectionMaxPoints(sections: Map[String, Int]): Int = {
    sections.values.max
  }

  private def getTeacherMax(): Int = {

    val maxHoursInWork = getSectionMaxPoints(rating.teacher_rating.total_hours_in_work)

    taskRatingHelper.teacher_rating_helper.hours_in_work.foldLeft(0) {
      case (sum, (_, teacherMap)) =>

        val hoursInWorkPoints = teacherMap.size * maxHoursInWork
        val daysInWorkPoints = getSectionMaxPoints(rating.teacher_rating.no_work_days_num)
        val monFriPoints = rating.teacher_rating.no_work_days_on_mon_fri

        sum + hoursInWorkPoints + daysInWorkPoints + monFriPoints
    }
  }

  private def countTeacherData(): Int = {

    taskRatingHelper.teacher_rating_helper.hours_in_work.foldLeft(0) {
      case (sum, (_, teacherMap)) =>

        val hoursInWorkPoints = teacherMap.map({ case (day, hoursInWork) =>
          val points = getSectionPoints(rating.teacher_rating.total_hours_in_work, hoursInWork)
          (day, points)
        }).values.sum

        val gapHoursPoints = teacherMap.map({ case (day, gapHours) =>
          val points = getSectionPoints(rating.teacher_rating.gap_hours.getOrElse(Map()), gapHours)
          (day, points)
        }).values.sum

        val daysInWork = teacherMap.count(x => x._2 > 0)
        val daysInWorkPoints = getSectionPoints(rating.teacher_rating.no_work_days_num, daysInWork)

        val isMonFriFree = teacherMap.contains("0") || teacherMap.contains("4")
        val monFriPoints = if (isMonFriFree) rating.teacher_rating.no_work_days_on_mon_fri else 0

        sum + hoursInWorkPoints + gapHoursPoints + daysInWorkPoints + monFriPoints
    }

  }

  private def getRoomMax(): Int = {

    val maxPoints = getSectionMaxPoints(rating.room_rating.too_big_capacity)
    taskRatingHelper.room_rating_helper.empty_chair_groups.foldLeft(0) {
      case (sum, (_, groups)) => sum + groups.size * maxPoints
    }

  }

  private def countRoomData(): Int = {

    taskRatingHelper.room_rating_helper.empty_chair_groups.foldLeft(0) {
      case (sum, (emptyChairs, groups)) =>
        sum + groups.size * getSectionPoints(rating.room_rating.too_big_capacity, emptyChairs.toInt)
    }

  }

  private def termToKey(term: Term): String = {
    "%03d".format(scheduler.Day.valueOf(term.day).get.value * 100 + term.start.hour)
  }

  private def getTermMax(): Int = {

    val groupCount = timetable.map(_.group).toSet.size

    val startEvenPoints = groupCount * rating.term_rating.start_even
    val startOddPoints = groupCount * rating.term_rating.start_odd
    val termsPoints = timetable.size * getSectionMaxPoints(rating.term_rating.terms)

    startEvenPoints + startOddPoints + termsPoints
  }

  private def countTermData(): Int = {

    val startEvenPoints = taskRatingHelper.term_rating_helper.start_even_groups.size *
      rating.term_rating.start_even
    val startOddPoints = taskRatingHelper.term_rating_helper.start_odd_groups.size *
      rating.term_rating.start_odd

    val termsPoints = timetable.map(_.term).groupBy(identity).mapValues(_.size).map({
      case (termId, num) =>
        num * rating.term_rating.terms.getOrElse(termToKey(termsMap(termId)), 0)
      }).sum

    startEvenPoints + startOddPoints + termsPoints
  }

  def getCoursePointsByTerm(course: String): Map[String, Double] = {
    val importantVotesByCourse = votesByCourse.getOrElse(course, Seq())
      .map(_.student)
      .toSet
      .flatMap({ student: String => votesByStudents.get(student) })
      .flatten
      .groupBy(_.course)

    importantVotesByCourse
      .map({ case (conflictingCourse, courseVotes) =>
        val votesTotalPoints = courseVotes.map(_.points).sum

        timetableByCourse.getOrElse(conflictingCourse, Seq())
          .filter(t => { t.group != group._id })
          .map({ t: GroupRoomTerm =>

            val group = groupsMap(t.group)

            val groupTypeGroupsNum = groupsNumByType(group.extra.course)(group.extra.group_type)
            val newPoints: Double = (1.0 / groupTypeGroupsNum) * votesTotalPoints

            Map(t.term -> newPoints)
          })
      })
      .toSeq
      .flatten
      .foldLeft[Map[String, Double]](Map())({ case (a, b) => a |+| b })
  }

  def countAllStudentData(): (Double, Double) = {
    val m: Map[String, Double] = getCoursePointsByTerm(group.extra.course)
    val resultTerms = newTimes.map(_._2).toSet

    val max = m.values.size match {
      case 0 => 0
      case _ => m.values.max
    }
    val termsSum = resultTerms.map(m.getOrElse(_, 0.0)).sum

    (termsSum, max * resultTerms.size)
  }

  def getRating(): Double = {

    val teacherCount = countTeacherData()
    val roomCount = countRoomData()
    val termCount = countTermData()

    val teacherMax = getTeacherMax()
    val roomMax = getRoomMax()
    val termMax = getTermMax()

    val (studentCount, studentMax) = countAllStudentData()

    val teacherPercent: Double = teacherCount.toDouble * 100.0 / teacherMax.toDouble
    val roomPercent: Double = roomCount.toDouble * 100.0 / roomMax.toDouble
    val termPercent: Double = termCount.toDouble * 100.0 / termMax.toDouble

    // the less conflicts the better, so 3% of conflicts is switched to 97% of points, and round
    // to the nearest 5
    val studentPercent: Double = studentMax match {
      case 0 => 100.0
      case _ => Math.round((100.0 - studentCount * 100.0 / studentMax) / 5) * 5
    }

    val weightedSum = teacherPercent * rating.weights.teacher_rating +
      roomPercent * rating.weights.room_rating +
      termPercent * rating.weights.term_rating +
      studentPercent * rating.weights.student_rating.getOrElse(0)

    val weightsSum = rating.weights.teacher_rating + rating.weights.room_rating +
      rating.weights.term_rating + rating.weights.student_rating.getOrElse(0)

    weightedSum / weightsSum
  }

}
