package com.mgr.scheduler.datastructures

import com.mgr.scheduler.datastructures.Implicits._
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler

case class File(
  id: String,
  config1: Config,
  config2: Config,
  lines: Seq[Line]
) {

  def addTeacher(teacher: scheduler.Teacher): File = {
    this.copy(
      config1 = config1.addTeacher(teacher),
      config2 = config2.addTeacher(teacher),
      lines = lines.addTeacher(teacher, config1.configId, config2.configId)
    )
  }

  def addGroup(group: scheduler.Group, config: docs.Config): File = {
    val newConfig1 = {
      if (config._id == config1.configId) {
        config1.addGroup(group)
      } else {
        config1
      }
    }
    val newConfig2 = {
      if (config._id == config2.configId) {
        config2.addGroup(group)
      } else {
        config2
      }
    }

    this.copy(
      config1 = newConfig1,
      config2 = newConfig2,
      lines = lines.addGroup(group, config)
    )
  }

  def isValid: (Option[String], Boolean) = {
    val valid1 = config1.isValid
    val valid2 = config2.isValid

    val err = (valid1._1, valid2._1) match {
      case (Some(s1), Some(s2)) => Some(s1 ++ s2)
      case (Some(s1), None) => Some(s1)
      case (None, Some(s2)) => Some(s2)
      case (None, None) => None
    }
    (err, valid1._2 && valid2._2)
  }

  def setNew(file: File): File = {
    val oldTeachers1 = config1.teachers
    val oldTeachers1Map = oldTeachers1.map(x => (x._id, x)).toMap
    val newTeachers1 = file.config1.teachers

    val teachers1 = newTeachers1.map { t =>
      oldTeachers1Map.get(t._id) match {
        case None => t
        case Some(oldt) => oldt.copy(extra=t.extra)
      }
    }

    val oldTeachers2 = config2.teachers
    val oldTeachers2Map = oldTeachers2.map(x => (x._id, x)).toMap
    val newTeachers2 = file.config2.teachers

    val teachers2 = newTeachers2.map { t =>
      oldTeachers2Map.get(t._id) match {
        case None => t
        case Some(oldt) => oldt.copy(extra=t.extra)
      }
    }

    val oldGroups1 = config1.groups
    val oldGroups1Map = oldGroups1.map(x => (x._id, x)).toMap
    val newGroups1 = file.config1.groups

    val groups1 = newGroups1.map { g =>
      oldGroups1Map.get(g._id) match {
        case None => g
        case Some(oldg) => oldg.copy(extra=g.extra, terms_num=g.terms_num, teachers=g.teachers)
      }
    }

    val oldGroups2 = config2.groups
    val oldGroups2Map = oldGroups2.map(x => (x._id, x)).toMap
    val newGroups2 = file.config2.groups

    val groups2 = newGroups2.map { g =>
      oldGroups2Map.get(g._id) match {
        case None => g
        case Some(oldg) => oldg.copy(extra=g.extra, terms_num=g.terms_num, teachers=g.teachers)
      }
    }

    val groupsMap = groups1.map(x => (x._id, x)).toMap ++ groups2.map(x => (x._id, x)).toMap
    val teachersMap = teachers1.map(x => (x._id, x)).toMap ++ teachers2.map(x => (x._id, x)).toMap

    val newConfig1 = config1.copy(teachers=teachers1, groups=groups1)
    val newConfig2 = config2.copy(teachers=teachers2, groups=groups2)

    val newLines = file.lines
      .mapGroup({ g => groupsMap(g._id)})
      .mapTeacher({ t => teachersMap(t._id)})

    File(id, newConfig1, newConfig2, newLines)
  }
}
