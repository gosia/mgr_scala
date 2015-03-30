package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Teacher(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  terms: Seq[String],

  `type`: String = Teacher.`type`
) extends Base {

  def isValid(validTerms: Set[String]): (Option[String], Boolean) = {
    if ((terms.toSet -- validTerms).isEmpty) {
      (None, true)
    } else {
      val unknownTerms = (terms.toSet -- validTerms).mkString(", ")
      (Some(s"Teacher $getRealId is not valid (unknown terms: <$unknownTerms>)"), false)
    }
  }

  def toTxt: String = getRealId

  def asThrift: scheduler.Teacher = scheduler.Teacher(
    this.getRealId,
    this.terms.map(Term.getRealId(_))
  )

}

object Teacher extends BaseObj {
  val `type`: String = "teacher"

  def apply(configId: String, teacher: scheduler.Teacher): Teacher = Teacher(
    _id = Teacher.getCouchId(configId, teacher.id),
    config_id = configId,
    terms = teacher.terms map { Term.getCouchId(configId, _) }
  )
}
