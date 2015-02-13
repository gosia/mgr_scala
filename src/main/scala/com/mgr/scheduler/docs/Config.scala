package com.mgr.scheduler.docs

import com.mgr.utils.couch

final case class Config(
  _id: String,
  _rev: Option[String] = None,

  `type`: String = "config"
) extends couch.Document
