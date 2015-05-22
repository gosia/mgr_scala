package com.mgr.scheduler.serializers

import com.mgr.scheduler.datastructures
import com.mgr.scheduler.docs

case class Ii(fileId: String, data: String) extends Base {

  val configId1: String = s"$fileId-1"
  val configId2: String = s"$fileId-2"

  def allTermsF(configId: String): Seq[docs.Term] = (0 to 5).map({ day =>
    (8 to 21).map { hour =>
      (day == 0 && hour < 12) || (day == 4 && hour >= 12) match {
        case true => None
        case false => Some(docs.Term.forIi(configId, day, hour))
      }
    }
  }).flatten.flatten

  val allTerms: Map[String, Seq[docs.Term]] = Map(
    configId1 -> allTermsF(configId1),
    configId2 -> allTermsF(configId2)
  )
  val allTermIds = allTerms mapValues { _.map(_._id) }

  private def getTeacherDocs(lines: Seq[String], configId: String): Seq[docs.Teacher] = {
    lines map { line =>
      val parts = line.split("\\|")
      val id = parts(1)

      // TODO(gosia): use those data
      // val firstName = parts(2)
      // val lastName = parts(4)
      // val pensum = parts(5)

      docs.Teacher(
        _id = docs.Teacher.getCouchId(configId, id),
        config_id = configId,
        terms = allTermIds(configId)
      )
    }
  }

  private def defaultRooms: Seq[String] = Seq(
    "r|103|38|wyklad,cwiczenia,103",
    "r|104|32|wyklad,cwiczenia,104",
    "r|105|32|wyklad,cwiczenia,105",
    "r|106|12|pracownia,106",
    "r|107|20|pracownia,107",
    "r|108|20|pracownia,108",
    "r|109|20|pracownia,109",
    "r|110|20|pracownia,110",
    "r|119|80|wyklad,119",
    "r|137|20|wyklad,cwiczenia,137",
    "r|139|32|wyklad,cwiczenia,139",
    "r|140|46|wyklad,cwiczenia,140",
    "r|141|48|wyklad,cwiczenia,141",
    "r|237|12|wyklad,cwiczenia,237",
    "r|310|10|wyklad,cwiczenia,310",
    "r|325|20|wyklad,cwiczenia,325",
    "r|4|32|pracownia,4",
    "r|5|32|pracownia,5",
    "r|7|10|pracownia,7",
    "r|25|200|wyklad,25"
  )

  private def getRoomDocs(configId: String): Seq[docs.Room] = {
    defaultRooms map { room =>
      val parts = room.split("\\|")
      val id = parts(1)
      val capacity = parts(2)
      val labels = parts(3).split(",")

      docs.Room(
        _id = docs.Room.getCouchId(configId, id),
        config_id = configId,
        terms = allTermIds(configId),
        labels = labels map { docs.Label.getCouchId(configId, _) },
        capacity = capacity.toInt
      )
    }
  }

  private def getLabelDocs(configId: String): Seq[docs.Label] = {
    defaultRooms map { room =>
      val parts = room.split("\\|")
      val labels = parts(3).split(",")

      labels map { label =>
        docs.Label(configId, label)
      }
    } flatten
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

  private def diffTermGroups(
    normLines: Seq[(Seq[String], Int)], configId: String
  ): Map[String, Seq[String]] = {

    val tGroups: Map[String, Set[Int]] = normLines.foldLeft(Map[String, Set[Int]]()) {
      case (m, (parts, groupId)) =>
        val teacher = parts(5)
        val newSet: Set[Int] = m.getOrElse(teacher, Set()) union Set(groupId)
        m ++ Map(teacher -> newSet)
    }

    val diffTermGroupTypes = Set("w", "e")
    val dGroups: Map[String, Set[Int]] = normLines.foldLeft(Map[String, Set[Int]]()) {
      case (m, (parts, groupId)) =>
        val (courseName, groupType) = (parts(2), parts(3))
        diffTermGroupTypes.contains(groupType) match {
          case false => m
          case true => m ++ Map(courseName -> (m.getOrElse(courseName, Set()) union Set(groupId)))
        }
    }

    val diffGroups = tGroups.values.toSeq ++ dGroups.values.toSeq

    val diffMap: Map[Int, Set[Int]] = diffGroups.foldLeft(Map[Int, Set[Int]]()) { case (m, set) =>
      set.foldLeft(m) { case (mm, groupId) =>
        mm ++ Map(groupId -> (mm.getOrElse(groupId, Set()) ++ set))
      }
    }

    diffMap map { case (k, v) => (
      docs.Group.getCouchId(configId, k.toString),
      (v - k).toSeq.map(x => docs.Group.getCouchId(configId, x.toString))
      ) } toMap
  }

  private def getGroupDocs(lines: Seq[String], configId: String): Seq[docs.Group] = {

    val normLines = normalizeGroupLines(lines)
    val diffGroups = diffTermGroups(normLines, configId)

    normLines map { case (parts, groupId) =>
      val (courseName, groupType, hours, teacherId) = (parts(2), parts(3), parts(4), parts(5))
      val id = docs.Group.getCouchId(configId, groupId.toString)

      docs.Group(
        _id = id,
        config_id = configId,
        diff_term_groups = diffGroups.getOrElse(id, Seq()),
        labels = roomLabels(configId).getOrElse(groupType, Seq()),
        same_term_groups = Seq(),
        teachers = Seq(docs.Teacher.getCouchId(configId, teacherId)),
        terms = allTermIds(configId),
        terms_num = hours.toInt,
        students_num = 15,
        extra = Some(docs.GroupExtra(course = courseName, group_type = groupType))
      )
    }

  }

  private def normalizeGroupLines(lines: Seq[String]): Seq[(Seq[String], Int)] = {
    val splittedLines = lines map { line =>
      val parts = line.split("\\|").toSeq
      parts.length match {
        case 6 => parts ++ Seq("")
        case _ => parts
      }
    }
    splittedLines.zipWithIndex
  }

  def toFileDef: datastructures.File = {
    val lines = data.split("\n")
    val tLines = lines filter { x => x.startsWith("o|")}
    val s1Lines = lines filter { x => x.startsWith("1|")}
    val s2Lines = lines filter { x => x.startsWith("2|")}

    val teachers1: Seq[docs.Teacher] = getTeacherDocs(tLines, configId1)
    val rooms1: Seq[docs.Room] = getRoomDocs(configId1)
    val labels1: Seq[docs.Label] = getLabelDocs(configId1)

    val teachers2: Seq[docs.Teacher] = getTeacherDocs(tLines, configId2)
    val rooms2: Seq[docs.Room] = getRoomDocs(configId2)
    val labels2: Seq[docs.Label] = getLabelDocs(configId2)

    val groups1 = getGroupDocs(s1Lines, configId1)
    val groups2 = getGroupDocs(s2Lines, configId2)

    val config1 = datastructures.Config(
      teachers = teachers1,
      labels = labels1,
      groups = groups1,
      terms = allTerms(configId1),
      rooms = rooms1,
      configId = configId1
    )
    val config2 = datastructures.Config(
      teachers = teachers2,
      labels = labels2,
      groups = groups2,
      terms = allTerms(configId2),
      rooms = rooms2,
      configId = configId2
    )

    datastructures.File(fileId, config1, config2)

  }
}
