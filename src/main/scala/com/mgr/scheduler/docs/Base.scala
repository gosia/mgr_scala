package com.mgr.scheduler.docs

import com.mgr.utils.couch

trait Base extends couch.Document {
  val config_id: String

  def getRealId = this._id.split(":").tail.mkString(":")
}

object Base {
  def getCouchId(configId: String, id: String) = s"$configId:$id"
}
