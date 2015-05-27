package com.mgr.scheduler.datastructures

import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler

sealed trait Line
case class EmptyLine(line: String) extends Line
case class TeacherLine(doc1: docs.Teacher, doc2: docs.Teacher) extends Line
case class GroupLine(doc: docs.Group, term: String) extends Line

class LineSeq(xs: Seq[Line]) {
  def mapTeacher(f: docs.Teacher => docs.Teacher): Seq[Line] = xs map {
    case EmptyLine(l) => EmptyLine(l)
    case TeacherLine(doc1, doc2) => TeacherLine(f(doc1), f(doc2))
    case GroupLine(doc, term) => GroupLine(doc, term)
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


  def mapGroup(f: docs.Group => docs.Group): Seq[Line] = xs map {
    case EmptyLine(l) => EmptyLine(l)
    case TeacherLine(doc1, doc2) => TeacherLine(doc1, doc2)
    case GroupLine(doc, term) => GroupLine(f(doc), term)
  }

  def teachers1: Seq[docs.Teacher] = xs map {
    case EmptyLine(l) => None
    case TeacherLine(doc1, doc2) => Some(doc1)
    case GroupLine(_, _) => None
  } flatten
  def teachers2: Seq[docs.Teacher] = xs map {
    case EmptyLine(l) => None
    case TeacherLine(doc1, doc2) => Some(doc2)
    case GroupLine(_, _) => None
  } flatten

  def groups1: Seq[docs.Group] = xs map {
    case EmptyLine(_) => None
    case TeacherLine(_, _) => None
    case GroupLine(doc, term) => term match {
      case "winter" => Some(doc)
      case _ => None
    }
  } flatten
  def groups2: Seq[docs.Group] = xs map {
    case EmptyLine(_) => None
    case TeacherLine(_, _) => None
    case GroupLine(doc, term) => term match {
      case "summer" => Some(doc)
      case _ => None
    }
  } flatten
}

object Implicits {
  implicit def seqToMyLineSeq(xs: Seq[Line]): LineSeq = new LineSeq(xs)
}
