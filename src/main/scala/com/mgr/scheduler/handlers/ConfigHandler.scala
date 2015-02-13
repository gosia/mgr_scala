package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.Client
import com.mgr.utils.couch.CouchResponse

object ConfigHandler {

  val couchClient = Client("localhost", 6666, "scheduler")

  def isValidConfig(
    id: String,
    terms: Seq[docs.Term],
    rooms: Seq[docs.Room],
    teachers: Seq[docs.Teacher],
    groups: Seq[docs.Group],
    labels: Seq[docs.Label]
  ): Boolean = {
    val termIds = terms map { _._id } toSet
    val roomIds = rooms map { _._id } toSet
    val teacherIds = teachers map { _._id } toSet
    val groupIds = groups map { _._id } toSet
    val labelIds = labels map { _._id } toSet

    terms.forall(_.isValid) &&
    rooms.forall(_.isValid(termIds, labelIds)) &&
    teachers.forall(_.isValid(termIds)) &&
    groups.forall(_.isValid(termIds, labelIds, groupIds))

    // TODO(gosia): Check symmetric of group.sameTermGroups and group.diffTermGroups
  }

  def createConfig(
    id: String,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    val termDocs = terms map { docs.Term(id, _) }
    val roomDocs = rooms map { docs.Room(id, _) }
    val teacherDocs = teachers map { docs.Teacher(id, _) }
    val groupDocs = groups map { docs.Group(id, _) }
    val configDoc = docs.Config(id)

    val labelIds = (rooms.map(_.labels) ++ groups.map(_.labels)).foldLeft(Set.empty[String])({
      case (s: Set[String], labels: Seq[String]) => s ++ labels.toSet
    }) toSeq
    val labelDocs = labelIds map { docs.Label(id, _) }

    if (!isValidConfig(id, termDocs, roomDocs, teacherDocs, groupDocs, labelDocs)) {
      throw scheduler.SchedulerException("Config is not valid")
    }

    val allDoc = (termDocs ++ roomDocs ++ teacherDocs ++ groupDocs ++ Seq(configDoc)) map {
      couchClient.add(_)
    }
    val responses: Future[Seq[CouchResponse]] = Future.collect(allDoc)
    responses map { _ => ()}
  }

}
