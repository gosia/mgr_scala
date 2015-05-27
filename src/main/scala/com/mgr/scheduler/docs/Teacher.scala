package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class TeacherExtra(
  first_name: String = "",
  last_name: String = "",
  pensum: Int = 0,
  notes: String = ""
)

final case class Teacher(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  terms: Seq[String],

  extra: TeacherExtra,

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
    this.terms.map(Term.getRealId),
    scheduler.TeacherExtra(
      extra.first_name,
      extra.last_name,
      extra.pensum.toShort,
      extra.notes
    )
  )

  def editConfig(newConfigId: String): Teacher = Teacher.apply(
    newConfigId, this.asThrift
  )

}

object Teacher extends BaseObj {
  val `type`: String = "teacher"

  def apply(configId: String, teacher: scheduler.Teacher): Teacher = Teacher(
    _id = Teacher.getCouchId(configId, teacher.id),
    config_id = configId,
    terms = teacher.terms map { Term.getCouchId(configId, _) },
    extra = TeacherExtra(
      first_name = teacher.extra.firstName,
      last_name = teacher.extra.lastName,
      pensum = teacher.extra.pensum,
      notes = teacher.extra.notes
    )
  )
}
