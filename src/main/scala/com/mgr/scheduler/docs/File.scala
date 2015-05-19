package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler
import com.mgr.utils.couch

final case class File(
  _id: String,
  _rev: Option[String] = None,

  year: Int,
  content: String,

  linked: Boolean,

  `type`: String = File.`type`
) extends couch.Document {

  def toThrift: scheduler.File = scheduler.File(
    scheduler.FileBasicInfo(_id, year.toShort, linked), content
  )

}

object File {
  val `type`: String = "file"

  def apply(info: scheduler.FileCreationInfo, content: String): File = File(
    _id = info.id,
    year = info.year,
    content = content,
    linked = false
  )
}

final case class FileInfoView(
  _id: String,
  year: Int,
  linked: Boolean
) {
  def toThrift: scheduler.FileBasicInfo = scheduler.FileBasicInfo(_id, year.toShort, linked)
}
