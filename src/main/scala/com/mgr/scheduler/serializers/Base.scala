package com.mgr.scheduler.serializers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.datastructures
import com.mgr.scheduler.datastructures.Implicits._
import com.mgr.scheduler.docs
import com.mgr.scheduler.handlers.ConfigHandler
import com.mgr.thrift.scheduler
import com.mgr.utils.logging.Logging

trait Base extends Logging with Couch {

  type TLinear <: datastructures.Linear
  val TLinearImpl: TLinear
  val file: docs.File

  val fileId: String = file._id
  val configId1: String = s"$fileId-1"
  val configId2: String = s"$fileId-2"

  def allTermsF(configId: String): Seq[docs.Term] = (0 to 4).flatMap({ day =>
    (8 to 21).map { hour =>
      (day == 0 && hour < 12) || (day == 4 && hour >= 12) match {
        case true => None
        case false => Some(docs.Term.forIi(configId, day, hour))
      }
    }
  }).flatten

  val allTerms: Map[String, Seq[docs.Term]] = Map(
    configId1 -> allTermsF(configId1),
    configId2 -> allTermsF(configId2)
  )
  val allTermIds = allTerms mapValues { _.map(_._id) }

  private def defaultRooms: Seq[String] = Seq(
    "r|103|38|wyklad,cwiczenia,103",
    "r|104|32|wyklad,cwiczenia,104",
    "r|105|32|wyklad,cwiczenia,105",
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
      val parts = room.split("\\|", -1)
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
    defaultRooms flatMap { room =>
      val parts = room.split("\\|", -1)
      val labels = parts(3).split(",")

      labels map { label =>
        docs.Label(configId, label)
      }
    }
  }

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

  private def fixStudentsNum(groups: Seq[docs.Group]): Seq[docs.Group] = {

    val map: Map[String, Map[String, Int]] =
      groups.foldLeft(Map[String, Map[String, Int]]()) { case (m, g) =>
        val newNum: Int = g.students_num +
          m.getOrElse(g.extra.course, Map()).getOrElse(g.extra.group_type, 0)
        val newMap: Map[String, Int] = m.getOrElse(g.extra.course, Map()) +
          (g.extra.group_type -> newNum)
        m + (g.extra.course -> newMap)
      }

    groups map { g => {
      g.extra.group_type match {
        case "w" | "e" =>
          val studentsNum = {
            val xs: Iterable[Int] = map.getOrElse(g.extra.course, Map()).values
            xs.size match {
              case 0 => 0
              case _ => xs.max
            }
          }
          g.copy(students_num = studentsNum)
        case _ => g
      }
    } }

  }

  private def toFileDefNoDb: datastructures.File = {
    val configId1: String = s"$fileId-1"
    val configId2: String = s"$fileId-2"

    val linesSeq = TLinearImpl.toLineSeq(fileId, file.content)

    val config1 = datastructures.Config(
      teachers = linesSeq.teachers1,
      labels = getLabelDocs(configId1),
      groups = fixStudentsNum(linesSeq.groups1),
      terms = allTermsF(configId1),
      rooms = getRoomDocs(configId1),
      configId = configId1
    )
    val config2 = datastructures.Config(
      teachers = linesSeq.teachers2,
      labels = getLabelDocs(configId2),
      groups = fixStudentsNum(linesSeq.groups2),
      terms = allTermsF(configId2),
      rooms = getRoomDocs(configId2),
      configId = configId2
    )

    datastructures.File(fileId, config1, config2, linesSeq)
  }

  private def toFileDefDb(file: docs.File): Future[datastructures.File] = {
    val configId1 = file.configs.map(_.configId1).getOrElse(
      throw scheduler.SchedulerException(s"Sth is wrong")
    )
    val configId2 = file.configs.map(_.configId2).getOrElse(
      throw scheduler.SchedulerException(s"Sth is wrong")
    )

    ConfigHandler.getConfigDef(configId1) flatMap {
      case (groups1, teachers1, rooms1, terms1, labels1) =>
        ConfigHandler.getConfigDef(configId2) map {
          case (groups2, teachers2, rooms2, terms2, labels2) =>

            val teacher1Map = teachers1 map { x: docs.Teacher => (x.getRealId, x) } toMap
            val teacher2Map = teachers2 map { x: docs.Teacher => (x.getRealId, x) } toMap
            val group1Map = groups1 map { x: docs.Group => (x.getRealId, x) } toMap
            val group2Map = groups2 map { x: docs.Group => (x.getRealId, x) } toMap

            val linesSeq = TLinearImpl.toLineSeqDb(
              file._id, file.content, group1Map, teacher1Map, group2Map, teacher2Map
            )

            val config1 = datastructures.Config(
              teachers = teachers1,
              labels = labels1,

              groups = groups1,
              terms = terms1,
              rooms = rooms1,
              configId = configId1
            )
            val config2 = datastructures.Config(
              teachers = teachers2,
              labels = labels2,
              groups = groups2,
              terms = terms2,
              rooms = rooms2,
              configId = configId2
            )

            datastructures.File(fileId, config1, config2, linesSeq)
        }
    }
  }

  def toFileDef: Future[datastructures.File] = {
    file.linked match {
      case false => Future.value(toFileDefNoDb)
      case true => toFileDefDb(file)
    }
  }

}
