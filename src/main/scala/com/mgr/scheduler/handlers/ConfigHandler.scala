package com.mgr.scheduler.handlers

import com.twitter.util.Future
import net.liftweb.json

import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.thrift.scheduler.TaskStatus
import com.mgr.utils.couch.Client
import com.mgr.utils.couch.CouchResponse
import com.mgr.utils.couch.ViewResult
import com.mgr.utils.logging.Logging

object ConfigHandler extends Logging {

  val couchClient = Client("localhost", 6666, "scheduler")

  def isValidConfig(
    id: String,
    terms: Seq[docs.Term],
    rooms: Seq[docs.Room],
    teachers: Seq[docs.Teacher],
    groups: Seq[docs.Group],
    labels: Seq[docs.Label]
  ): (Option[String], Boolean) = {
    val termIds = terms map { _._id } toSet
    val groupIds = groups map { _._id } toSet
    val labelIds = labels map { _._id } toSet

    val validated: Seq[(Option[String], Boolean)] =
      terms.map(_.isValid) ++
      rooms.map(_.isValid(termIds, labelIds)) ++
      teachers.map(_.isValid(termIds)) ++
      groups.map(_.isValid(termIds, labelIds, groupIds))

    val validationResult = validated.foldLeft[(Option[String], Boolean)]((None, true))({
      case (x, r) => if (x._2) {
        r
      } else {
        (r._1, x._1) match {
          case (None, None) => (None, false)
          case (Some(msg), None) => (Some(msg), false)
          case (None, Some(msg)) => (Some(msg), false)
          case (Some(msg1), Some(msg2)) => (Some(s"$msg1. $msg2"), false)
        }
      }
    })

    // TODO(gosia): Check symmetric of group.sameTermGroups and group.diffTermGroups
    validationResult
  }

  def createConfig(
    id: String,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    log.info(s"Adding config $id")
    val termDocs = terms map { docs.Term(id, _) }
    val roomDocs = rooms map { docs.Room(id, _) }
    val teacherDocs = teachers map { docs.Teacher(id, _) }
    val groupDocs = groups map { docs.Group(id, _) }
    val configDoc = docs.Config(id)

    val labelIds = (rooms.map(_.labels) ++ groups.map(_.labels)).foldLeft(Set.empty[String])({
      case (s: Set[String], labels: Seq[String]) => s ++ labels.toSet
    }) toSeq
    val labelDocs = labelIds map { docs.Label(id, _) }

    val valid = isValidConfig(id, termDocs, roomDocs, teacherDocs, groupDocs, labelDocs)
    if (!valid._2) {
      throw scheduler.SchedulerException(s"Config is not valid: ${valid._1}")
    }

    val allDocs = termDocs ++ roomDocs ++ teacherDocs ++ groupDocs ++ Seq(configDoc)

    couchClient.bulkAdd(allDocs).map { rseq: Seq[CouchResponse] => {
      val errors: Seq[String] = rseq.map(_.errorMsg).flatten
      errors.length match {
        case 0 => ()
        case n if n > 0 =>
          log.warning(s"Errors with bulk add: ${errors.mkString(", ")}")
          ()
      }
    }}
  }

  def getConfigTasks(configId: String): Future[Seq[scheduler.TaskInfo]] = {
    log.info(s"Getting tasks for config $configId")

    val query = couchClient.view("tasks/by_config").startkey(configId).endkey(configId).includeDocs

    query.execute map { result: ViewResult => result mapDocs {
      doc: docs.Task => scheduler.TaskInfo(doc._id, TaskStatus.valueOf(doc.status).get)
    }}
  }

}
