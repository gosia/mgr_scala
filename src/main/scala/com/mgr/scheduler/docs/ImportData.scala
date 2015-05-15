package com.mgr.scheduler.docs

final case class ImportData(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  data: String,

  `type`: String = ImportData.`type`
) extends Base

object ImportData extends BaseObj {
  val `type`: String = "import_data"

  def apply(configId: String, data: String): ImportData = ImportData(
    _id = ImportData.getCouchId(configId, ""),
    config_id = configId,
    data = data
  )
}
