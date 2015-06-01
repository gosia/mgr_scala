package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class GroupExtra(
  course: String,
  group_type: String,
  notes: String
)

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

  extra: GroupExtra,

  `type`: String = Group.`type`
) extends Base {

  def isValid(
    validTerms: Set[String], validLabels: Set[String], validGroups: Set[String],
    validTeachers: Set[String]
  ): (Option[String], Boolean) =
    if (
      (terms.toSet -- validTerms).isEmpty &&
      (labels.toSet -- validLabels).isEmpty &&
      (diff_term_groups.toSet -- validGroups).isEmpty &&
      (same_term_groups.toSet -- validGroups).isEmpty &&
      (teachers.toSet -- validTeachers).isEmpty
    ) {
      (None, true)
    } else {
      val unknownTerms = (terms.toSet -- validTerms).mkString(", ")
      val unknownLabels = (labels.toSet -- validLabels).mkString(", ")
      val unknownGroups = (diff_term_groups.toSet ++ same_term_groups.toSet) -- validGroups
      val unknownTeachers = (teachers.toSet -- validTeachers) -- validGroups
      (
        Some(
          s"Group $getRealId is not valid (unknown labels: <$unknownLabels>, " +
          s"unknown terms: <$unknownTerms>, unknown groups: <$unknownGroups>), " +
          s"unknown teachers: <$unknownTeachers>"
        ), false
      )
    }

  def toTxt: String = {
    s"${extra.course} (${extra.group_type})"
  }

  def asThrift: scheduler.Group = scheduler.Group(
    id=this.getRealId,
    teachers=this.teachers.map(Teacher.getRealId),
    terms=this.terms.map(Term.getRealId),
    labels=this.labels.map(Label.getRealId),
    diffTermGroups=this.diff_term_groups.map(Group.getRealId),
    sameTermGroups=this.same_term_groups.map(Group.getRealId),
    termsNum=this.terms_num.toShort,
    studentsNum=this.students_num.toShort,
    extra=scheduler.GroupExtra(
      extra.course,
      extra.group_type,
      extra.notes
    )
  )

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
    students_num = group.studentsNum,
    extra = GroupExtra(
      course = group.extra.course, group_type=group.extra.groupType, notes=group.extra.notes
    )
  )
}
