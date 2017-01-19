package com.mgr.scheduler.docs

import com.mgr.utils.couch
import com.mgr.utils.logging.Logging

trait Base extends couch.Document with Logging {
  val config_id: String

  def getRealId: String = this._id.split(":").tail.tail.mkString(":")
  def getRealIdNum: Int = this._id.split(":").last.toInt
}

trait BaseObj {
  val `type`: String

  def getCouchId(configId: String, id: String): String = s"$configId:${`type`}:$id"
  def getRealId(doc_id: String): String = doc_id.split(":").tail.tail.mkString(":")
}


final case class BaseDoc(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  `type`: String
) extends couch.Document
