package com.mgr.scheduler.docs

import com.mgr.utils.couch
import com.mgr.utils.logging.Logging

trait Base extends couch.Document with Logging {
  val config_id: String

  def getRealId = this._id.split(":").tail.tail.mkString(":")
}

trait BaseObj {
  val `type`: String

  def getCouchId(configId: String, id: String) = s"$configId:${`type`}:$id"
  def getRealId(doc_id: String) = doc_id.split(":").tail.tail.mkString(":")
}
