package com.mgr.scheduler.docs

final case class Label(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  `type`: String = Label.`type`
) extends Base

object Label extends BaseObj {
  val `type`: String = "label"

  def apply(configId: String, label: String): Label = Label(
    _id = Label.getCouchId(configId, label),
    config_id = configId
  )
}
