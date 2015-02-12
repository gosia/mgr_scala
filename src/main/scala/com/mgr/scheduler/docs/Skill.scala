package com.mgr.scheduler.docs

import com.mgr.utils.couch

final case class Skill(
  _id: String,
  _rev: Option[String] = None,

  `type`: String = "skill"
) extends couch.Document
