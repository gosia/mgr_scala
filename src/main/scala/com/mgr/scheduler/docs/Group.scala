package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Group(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  diff_term_groups: Seq[String],
  labels: Seq[String],
  same_term_groups: Seq[String],
  teachers: Seq[String],
  terms: Seq[String],
  terms_num: Int,
  students_num: Int,

  `type`: String = Group.`type`
) extends Base {

  def isValid(
    validTerms: Set[String], validLabels: Set[String], validGroups: Set[String]
  ): (Option[String], Boolean) =
    if (
      (terms.toSet -- validTerms).isEmpty &&
      (labels.toSet -- validLabels).isEmpty &&
      (diff_term_groups.toSet -- validGroups).isEmpty &&
      (same_term_groups.toSet -- validGroups).isEmpty
    ) {
      (None, true)
    } else {
      val unknownTerms = (terms.toSet -- validTerms).mkString(", ")
      val unknownLabels = (labels.toSet -- validLabels).mkString(", ")
      val unknownGroups = (diff_term_groups.toSet ++ same_term_groups.toSet) -- validGroups
      (
        Some(
          s"Group $getRealId is not valid (unknown labels: <$unknownLabels>, " +
          s"unknown terms: <$unknownTerms>, unknown groups: <$unknownGroups>)"
        ), false
      )
    }

  def toTxt: String = getRealId

}

object Group extends BaseObj {
  val `type` = "group"

  def apply(configId: String, group: scheduler.Group): Group = Group(
    _id = Group.getCouchId(configId, group.id),
    config_id = configId,
    terms = group.terms map { Term.getCouchId(configId, _) },
    labels = group.labels map { Label.getCouchId(configId, _) },
    teachers = group.teachers map { Teacher.getCouchId(configId, _) },
    terms_num = group.termsNum,
    diff_term_groups = group.diffTermGroups map { Group.getCouchId(configId, _) },
    same_term_groups = group.sameTermGroups map { Group.getCouchId(configId, _) },
    students_num = group.studentsNum
  )
}
