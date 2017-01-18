package com.mgr.scheduler.serializers

import com.mgr.scheduler.Couch
import com.mgr.scheduler.datastructures
import com.mgr.scheduler.docs


case class Ii(file: docs.File) extends Base with Couch {
  type TLinear = IiLinear
  val TLinearImpl = new TLinear {}
}
object Ii {
  def apply(fileId: String, content: String): Ii = Ii(docs.File(
    year = 2000, _id=fileId, content=content, linked=false, configs=None
  ))
}

trait IiLinear extends datastructures.Linear {

  def toLine(fileId: String, text: String): datastructures.Line = {
    val configId1: String = s"$fileId-1"
    val configId2: String = s"$fileId-2"

    text match {
      case line if line.startsWith("o|") =>
        datastructures.TeacherLine(
          getTeacherDoc(line, configId1),
          getTeacherDoc(line, configId2)
        )
      case line if line.startsWith("1|") =>
        datastructures.GroupLine(getGroupDoc(line, configId1), "winter")
      case line if line.startsWith("2|") =>
        datastructures.GroupLine(getGroupDoc(line, configId2), "summer")
      case line =>
        datastructures.EmptyLine(line)
    }
  }

  def toLineDb(
    fileId: String, text: String,
    groups1Map: Map[String, docs.Group], teachers1Map: Map[String, docs.Teacher],
    groups2Map: Map[String, docs.Group], teachers2Map: Map[String, docs.Teacher]
  ): datastructures.Line = {
    text match {
      case line if line.startsWith("o|") =>
        val teacher = line.split("\\|", -1)(1)
        datastructures.TeacherLine(teachers1Map(teacher), teachers2Map(teacher))
      case line if line.startsWith("1|") =>
        val groupId = line.split("\\|", -1)(1)
        datastructures.GroupLine(groups1Map(groupId), "winter")
      case line if line.startsWith("2|") =>
        val groupId = line.split("\\|", -1)(1)
        datastructures.GroupLine(groups2Map(groupId), "summer")
      case line => datastructures.EmptyLine(line)
    }

  }

  def fromLine(line: datastructures.Line): String = {
    line match {
      case datastructures.EmptyLine(l) => l
      case datastructures.TeacherLine(t, _) =>
        val firstLetter = t.extra.first_name.headOption.map(_.toUpper).getOrElse("")
        s"o|${t.getRealId}|${t.extra.first_name}|$firstLetter|${t.extra.last_name}|" +
          s"${t.extra.pensum}|${t.extra.notes}"
      case datastructures.GroupLine(g, term) =>
        val termNum = term match {
          case "winter" => 1
          case _ => 2
        }
        val id = g.getRealId
        val course = g.extra.course
        val groupType = g.extra.group_type
        val notes = g.extra.notes
        val teachers = g.teachers.map(docs.Teacher.getRealId).mkString(",")
        val hours = g.terms_num
        s"$termNum|$id|$course|$groupType|$hours|$teachers|$notes"
    }
  }

  private def allTerms(configId: String): Seq[docs.Term] = (0 to 4).map({ day =>
    (8 to 21).map { hour =>
      (day == 0 && hour < 12) || (day == 4 && hour >= 12) match {
        case true => None
        case false => Some(docs.Term.forIi(configId, day, hour))
      }
    }
  }).flatten.flatten
  private def allTermIds(configId: String): Seq[String] = {
    allTerms(configId) map { _._id }
  }

  private def getTeacherDoc(line: String, configId: String): docs.Teacher = {
    val parts = line.split("\\|", -1)
    val id = parts(1)

    val firstName = parts(2)
    val lastName = parts(4)
    val pensum = parts(5)
    val notes = parts(6)

    docs.Teacher(
      _id = docs.Teacher.getCouchId(configId, id),
      config_id = configId,
      terms = allTermIds(configId),
      extra = docs.TeacherExtra(firstName, lastName, pensum.toInt, notes)
    )
  }

  private def roomLabels(configId: String): Map[String, Seq[String]] = Map(
    "w" -> Seq("wyklad"),
    "e" -> Seq("wyklad"),
    "c" -> Seq("cwiczenia"),
    "p" -> Seq("pracownia"),
    "r" -> Seq("cwiczenia", "pracownia"),
    "s" -> Seq("cwiczenia"),
    "l" -> Seq("pracownia")
  ).mapValues { xs => xs.map(docs.Label.getCouchId(configId, _)) }

  private def getGroupDoc(line: String, configId: String): docs.Group = {
    val p = line.split("\\|", -1)
    val (groupId, courseName, groupType, hours, teacherId, notes) =
      (p(1), p(2), p(3), p(4), p(5), p(6))

    val id = docs.Group.getCouchId(configId, groupId.toString)

    docs.Group(
      _id = id,
      config_id = configId,
      diff_term_groups = Seq(), // TODO(gosia): this needs to be done
      labels = roomLabels(configId).getOrElse(groupType, Seq()),
      same_term_groups = Seq(),
      teachers = Seq(docs.Teacher.getCouchId(configId, teacherId)),
      terms = allTermIds(configId),
      terms_num = hours.toInt,
      students_num = 15,
      extra = docs.GroupExtra(course = courseName, group_type = groupType, notes = notes)
    )
  }

}
