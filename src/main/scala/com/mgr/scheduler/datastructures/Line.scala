package com.mgr.scheduler.datastructures

import com.mgr.scheduler.datastructures
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler

sealed trait Line {
  def setChanges(line: String): Line
}
case class EmptyLine(line: String) extends Line {
  def setChanges(line: String): Line = EmptyLine(line)
}
case class TeacherLine(doc1: docs.Teacher, doc2: docs.Teacher) extends Line {
  def setChanges(line: String): Line = {
    val parts = line.split("\\|", -1)
    val id = parts(1)

    val firstName = parts(2)
    val lastName = parts(4)
    val pensum = parts(5)
    val notes = parts(6)

    val t1 = docs.Teacher(
      _id = docs.Teacher.getCouchId(doc1.config_id, id),
      config_id = doc1.config_id,
      terms = doc1.terms,
      extra = docs.TeacherExtra(firstName, lastName, pensum.toInt, notes)
    )

    val t2 = docs.Teacher(
      _id = docs.Teacher.getCouchId(doc2.config_id, id),
      config_id = doc2.config_id,
      terms = doc2.terms,
      extra = docs.TeacherExtra(firstName, lastName, pensum.toInt, notes)
    )
    TeacherLine(t1, t2)
  }
}
case class GroupLine(doc: docs.Group, term: String) extends Line {
  def setChanges(line: String): Line = {
    val p = line.split("\\|", -1)
    val (courseName, groupType, hours, teacherIds, notes) = (p(2), p(3), p(4), p(5), p(6))
    val group = doc.copy(
      teachers = teacherIds.split(",").map(docs.Teacher.getCouchId(doc.config_id, _)),
      terms_num = hours.toInt,
      extra = docs.GroupExtra(course = courseName, group_type = groupType, notes = notes)
    )
    val term = p(0) match {
      case "1" => "winter"
      case _ => "summer"
    }
    GroupLine(group, term)
  }
}

class LineSeq(xs: Seq[Line]) {
  def mapTeacher(f: docs.Teacher => docs.Teacher): Seq[Line] = xs map {
    case EmptyLine(l) => EmptyLine(l)
    case TeacherLine(doc1, doc2) => TeacherLine(f(doc1), f(doc2))
    case GroupLine(doc, term) => GroupLine(doc, term)
  }

  def mapGroup(f: docs.Group => docs.Group): Seq[Line] = xs map {
    case EmptyLine(l) => EmptyLine(l)
    case TeacherLine(doc1, doc2) => TeacherLine(doc1, doc2)
    case GroupLine(doc, term) => GroupLine(f(doc), term)
  }

  def addTeacher(teacher: scheduler.Teacher, config1: String, config2: String): Seq[Line] = {
    val doc1 = docs.Teacher(config1, teacher)
    val doc2 = docs.Teacher(config2, teacher)
    Seq(TeacherLine(doc1, doc2)) ++ xs
  }

  def addGroup(group: scheduler.Group, config: docs.Config): Seq[Line] = {
    val doc = docs.Group(config._id, group)
    xs :+ GroupLine(doc, config.term)
  }

  def removeElement(elementId: String, elementType: String): Seq[Line] = {
    xs filterNot {
      case EmptyLine(l) => false
      case TeacherLine(doc1, doc2) => elementType match {
        case "teacher" => doc1.getRealId == elementId || doc2.getRealId == elementId
        case _ => false
      }
      case GroupLine(doc, _) => elementType match {
        case "group" => doc.getRealId == elementId
        case _ => false
      }
    }
  }

  def teachers1: Seq[docs.Teacher] = xs flatMap {
    case EmptyLine(l) => None
    case TeacherLine(doc1, doc2) => Some(doc1)
    case GroupLine(_, _) => None
  }
  def teachers2: Seq[docs.Teacher] = xs flatMap {
    case EmptyLine(l) => None
    case TeacherLine(doc1, doc2) => Some(doc2)
    case GroupLine(_, _) => None
  }

  def groups1: Seq[docs.Group] = xs flatMap {
    case EmptyLine(_) => None
    case TeacherLine(_, _) => None
    case GroupLine(doc, term) => term match {
      case "winter" => Some(doc)
      case _ => None
    }
  }
  def groups2: Seq[docs.Group] = xs flatMap {
    case EmptyLine(_) => None
    case TeacherLine(_, _) => None
    case GroupLine(doc, term) => term match {
      case "summer" => Some(doc)
      case _ => None
    }
  }
}

trait Linear {
  def toLine(fileId: String, text: String): Line
  def toLineDb(
    fileId: String, text: String,
    groups1Map: Map[String, docs.Group], teachers1Map: Map[String, docs.Teacher],
    groups2Map: Map[String, docs.Group], teachers2Map: Map[String, docs.Teacher]
  ): Line
  def fromLine(line: Line): String

  def toLineSeq(fileId: String, text: String): Seq[datastructures.Line] =
    text.split("\n", -1) map { toLine(fileId, _) }

  def toLineSeqDb(
    fileId: String, text: String,
    groups1Map: Map[String, docs.Group], teachers1Map: Map[String, docs.Teacher],
    groups2Map: Map[String, docs.Group], teachers2Map: Map[String, docs.Teacher]
  ): Seq[datastructures.Line] = text.split("\n", -1) map {
    toLineDb(fileId, _, groups1Map, teachers1Map, groups2Map, teachers2Map)
  }

  def fromLineSeq(lines: Seq[Line]): String = lines map fromLine mkString "\n"
}

object Implicits {
  implicit def seqToMyLineSeq(xs: Seq[Line]): LineSeq = new LineSeq(xs)
}
