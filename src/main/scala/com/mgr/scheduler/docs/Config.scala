package com.mgr.scheduler.docs

import com.mgr.utils.couch

final case class Config(
  _id: String,
  _rev: Option[String] = None,

  year: Int,
  term: String,

  file: Option[String] = None,

  `type`: String = "config"
) extends couch.Document {

  def theOtherConfigId: Option[String] = {
    file map { fileId => if (_id.endsWith("-1")) { s"$fileId-2" } else { s"$fileId-1" } }
  }

}
