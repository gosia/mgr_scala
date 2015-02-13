package com.mgr.scheduler.docs

final case class Label(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  `type`: String = "label"
) extends Base

object Label {
  def apply(configId: String, label: String): Label = Label(
    _id = Base.getCouchId(configId, label),
    config_id = configId
  )
}
