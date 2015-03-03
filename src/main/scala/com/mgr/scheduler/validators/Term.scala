package com.mgr.scheduler.validators

import com.mgr.scheduler.docs
import com.mgr.utils.logging.Logging

object Term extends Logging {

  def validSameGroup(
    group: docs.Group, allTerms: Set[String], timetable: Map[String, Seq[(String, String)]]
  ): Set[String] = {
    group.same_term_groups.length match {
      case 0 => allTerms
      case n if n > 0 =>
        group.same_term_groups.map(timetable.get(_)).flatten.flatten.map(_._2).toSet
    }
  }

  def validDiffGroup(
    group: docs.Group, allTerms: Set[String], timetable: Map[String, Seq[(String, String)]]
  ): Set[String] = {
    allTerms -- group.diff_term_groups.map(timetable.get(_)).flatten.flatten.map(_._2).toSet
  }

  def validTeachers(group: docs.Group, teacherMap: Map[String, Set[String]]): Set[String] = {
    val sets: Seq[Set[String]] = group.teachers.map(teacherMap.get(_).get)
    sets.reduce[Set[String]]({case (x, y) => x.intersect(y)})
  }

  def getIds(
    group: docs.Group,
    allTerms: Set[String],
    timetable: Map[String, Seq[(String, String)]],
    teacherMap: Map[String, Set[String]]
  ): Set[String] = {
    log.info(s"Getting valid term ids for group ${group._id}")
    validSameGroup(group, allTerms, timetable) &
      validDiffGroup(group, allTerms, timetable) &
      validTeachers(group, teacherMap)
  }
}
