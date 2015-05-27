package com.mgr.scheduler.docs

import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.utils.couch

final case class FileConfigs(
  configId1: String,
  configId2: String
)

final case class File(
  _id: String,
  _rev: Option[String] = None,

  year: Int,
  content: String,

  linked: Boolean,
  configs: Option[FileConfigs],

  `type`: String = File.`type`
) extends couch.Document {

  def toThrift: scheduler.File = scheduler.File(
    scheduler.FileBasicInfo(
      _id, year.toShort, linked,
      configs.map(x => scheduler.FileConfigs(x.configId1, x.configId2))
    ),
    content
  )

  def link(configId1: String, configId2: String): File = this.copy(
    configs = Some(FileConfigs(configId1, configId2)),
    linked = true
  )

  def addTeacher(t: Teacher): File = {
    val newContent = s"o|${t.getRealId}|???|K|???|0|0\n$content"
    this.copy(content = newContent)
  }

  def addGroup(g: Group, config: Config): File = {
    val term = config.term match {
      case "winter" => 1
      case _ => 2
    }
    val course = g.extra.map(_.course).getOrElse("???")
    val groupType = g.extra.map(_.group_type).getOrElse("???")
    val teachers = g.teachers.map(Teacher.getRealId).mkString(",")
    val hours = g.terms_num
    val newContent = s"$content\n$term|0|$course|$groupType|$hours|$teachers|?\n"
    this.copy(content = newContent)
  }

  def addElements(teachers: Seq[Teacher], groups: Seq[Group], config: Config): File = {
    val fileWithTeachers = teachers.foldLeft(this) { case (file, t) => file.addTeacher(t) }
    val fileWithGroups = groups.foldLeft(fileWithTeachers) {
      case (file, g) => file.addGroup(g, config)
    }
    fileWithGroups
  }

}

object File {
  val `type`: String = "file"

  def apply(info: scheduler.FileCreationInfo, content: String): File = File(
    _id = info.id,
    year = info.year,
    content = content,
    linked = false,
    configs = None
  )
}

final case class FileInfoView(
  _id: String,
  year: Int,
  linked: Boolean,
  configs: Option[FileConfigs]
) {
  def toThrift: scheduler.FileBasicInfo = scheduler.FileBasicInfo(
    _id, year.toShort, linked,
    configs.map(x => scheduler.FileConfigs(x.configId1, x.configId2))
  )
}
