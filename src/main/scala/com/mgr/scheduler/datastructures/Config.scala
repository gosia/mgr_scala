package com.mgr.scheduler.datastructures

import com.mgr.scheduler.docs
import com.mgr.scheduler.handlers

case class Config(
  teachers: Seq[docs.Teacher],
  labels: Seq[docs.Label],
  groups: Seq[docs.Group],
  terms: Seq[docs.Term],
  rooms: Seq[docs.Room],
  configId: String
) {
  val allDocs = this.teachers ++ this.labels ++ this.groups ++ this.terms ++ this.rooms

  def isValid: (Option[String], Boolean) = {
    handlers.ConfigHandler.isValidConfig(configId, terms, rooms, teachers, groups, labels)
  }

}
