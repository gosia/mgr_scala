package com.mgr.scheduler.docs

import com.mgr.utils.couch


final case class Time(
  day: Int,
  hour: Int,
  minutes: Int
)

final case class Term(
  _id: String,
  _rev: Option[String] = None,
  scheduling_id: String,

  start: Option[Time],
  end: Option[Time],

  `type`: String = "term"
) extends couch.Document
