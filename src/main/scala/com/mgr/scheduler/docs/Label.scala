package com.mgr.scheduler.docs

import com.mgr.utils.couch

final case class Label(
  _id: String,
  _rev: Option[String] = None,
  scheduling_id: String,

  `type`: String = "label"
) extends couch.Document
