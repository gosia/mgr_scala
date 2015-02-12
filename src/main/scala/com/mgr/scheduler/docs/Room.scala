package com.mgr.scheduler.docs

import com.mgr.utils.couch

final case class Room(
  _id: String,
  _rev: Option[String] = None,
  scheduling_id: String,

  availability: Seq[String],
  conflicting: Seq[String],
  labels: Seq[String],
  not_conflicting: Seq[String],
  terms_num: Int,

  `type`: String = "room"
) extends couch.Document
