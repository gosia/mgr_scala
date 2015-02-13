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

  `type`: String = "group"
) extends Base {

  def isValid(
    validTerms: Set[String], validLabels: Set[String], validGroups: Set[String]
  ): Boolean =
    (terms.toSet - validTerms).isEmpty &&
    (labels.toSet - validLabels).isEmpty &&
    (diff_term_groups.toSet - validGroups).isEmpty &&
    (same_term_groups.toSet - validGroups).isEmpty

}

object Group {
  def apply(configId: String, group: scheduler.Group): Group = Group(
    _id = Base.getCouchId(configId, group.id),
    config_id = configId,
    terms = group.terms map { Base.getCouchId(configId, _) },
    labels = group.labels map { Base.getCouchId(configId, _) },
    teachers = group.teachers map { Base.getCouchId(configId, _) },
    terms_num = group.termsNum,
    diff_term_groups = group.diffTermGroups map { Base.getCouchId(configId, _) },
    same_term_groups = group.sameTermGroups map { Base.getCouchId(configId, _) }
  )
}
