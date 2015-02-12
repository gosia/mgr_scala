package com.mgr.scheduler.docs

import com.mgr.utils.couch

final case class Group(
  _id: String,
  _rev: Option[String] = None,
  scheduling_id: String,

  availability: Seq[String],
  labels: Seq[String],
  teachers: Seq[String],

  `type`: String = "teacher"
) extends couch.Document
