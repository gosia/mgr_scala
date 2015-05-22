package com.mgr.scheduler.docs

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
