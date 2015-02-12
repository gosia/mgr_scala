package com.mgr.scheduler.docs

import com.mgr.utils.couch

final case class Teacher(
  _id: String,
  _rev: Option[String] = None,
  scheduling_id: String,

  availability: Seq[String],

  `type`: String = "teacher"
) extends couch.Document
