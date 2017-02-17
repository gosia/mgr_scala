package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler


case class TaskRating(
  rating: Rating,
  taskRatingHelper: TaskRatingHelper,
  timetable: Seq[GroupRoomTerm],
  termsMap: Map[String, Term]
) {

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

  def getRating(): Double = {

    val teacherCount = countTeacherData()
    val roomCount = countRoomData()
    val termCount = countTermData()

    val teacherMax = getTeacherMax()
    val roomMax = getRoomMax()
    val termMax = getTermMax()

    val teacherPercent: Double = teacherCount.toDouble * 100.0 / teacherMax.toDouble
    val roomPercent: Double = roomCount.toDouble * 100.0 / roomMax.toDouble
    val termPercent: Double = termCount.toDouble * 100.0 / termMax.toDouble

    val weightedSum = teacherPercent * rating.weights.teacher_rating +
      roomPercent * rating.weights.room_rating +
      termPercent * rating.weights.term_rating

    val weightsSum = rating.weights.teacher_rating + rating.weights.room_rating +
      rating.weights.term_rating

    weightedSum / weightsSum
  }

}
