package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler
import com.mgr.utils.couch

final case class Teacher(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  terms: Seq[String],

  `type`: String = "teacher"
) extends couch.Document {

  def isValid(validTerms: Set[String]): Boolean = (terms.toSet - validTerms).isEmpty

}

object Teacher {
  def apply(configId: String, teacher: scheduler.Teacher): Teacher = Teacher(
    _id = Base.getCouchId(configId, teacher.id),
    config_id = configId,
    terms = teacher.terms map { Base.getCouchId(configId, _) }
  )
}
