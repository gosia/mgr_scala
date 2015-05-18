package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.config.Config
import com.mgr.scheduler.docs
import com.mgr.scheduler.serializers
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.Client
import com.mgr.utils.couch.CouchResponse
import com.mgr.utils.couch.ViewResult
import com.mgr.utils.logging.Logging

object ConfigHandler extends Logging {

  lazy val couchClient = Client(Config.couchHost, Config.couchPort, "scheduler")

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
    val teacherIds = teachers map { _._id } toSet

    val validated: Seq[(Option[String], Boolean)] =
      terms.map(_.isValid) ++
      rooms.map(_.isValid(termIds, labelIds)) ++
      teachers.map(_.isValid(termIds)) ++
      groups.map(_.isValid(termIds, labelIds, groupIds, teacherIds))

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
    info: scheduler.ConfigBasicInfo,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    val id = info.id
    log.info(s"Adding config $id")

    val termDocs = terms map { docs.Term(id, _) }
    val roomDocs = rooms map { docs.Room(id, _) }
    val teacherDocs = teachers map { docs.Teacher(id, _) }
    val groupDocs = groups map { docs.Group(id, _) }
    val configDoc = docs.Config(_id=id, year=info.year, term=info.term)

    val labelIds = (rooms.map(_.labels) ++ groups.map(_.labels)).foldLeft(Set.empty[String])({
      case (s: Set[String], labels: Seq[String]) => s ++ labels.toSet
    }) toSeq
    val labelDocs = labelIds map { docs.Label(id, _) }

    val valid = isValidConfig(id, termDocs, roomDocs, teacherDocs, groupDocs, labelDocs)
    if (!valid._2) {
      throw scheduler.SchedulerException(s"Config is not valid: ${valid._1}")
    }

    val allDocs = termDocs ++ roomDocs ++ teacherDocs ++ groupDocs ++ labelDocs ++ Seq(configDoc)

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

  def getConfigDef(configId: String): Future[(
    Seq[docs.Group], Seq[docs.Teacher], Seq[docs.Room], Seq[docs.Term], Seq[docs.Label]
  )] = {
    log.info(s"Getting definition for config $configId")

    val groupQ = couchClient.view("groups/by_config")
      .startkey(configId).endkey(configId).includeDocs
    val teacherQ = couchClient.view("teachers/by_config")
      .startkey(configId).endkey(configId).includeDocs
    val roomQ = couchClient.view("rooms/by_config").startkey(configId).endkey(configId).includeDocs
    val termQ = couchClient.view("terms/by_config").startkey(configId).endkey(configId).includeDocs
    val labelQ = couchClient.view("labels/by_config").startkey(configId).endkey(configId)
      .includeDocs

    groupQ.execute flatMap { groupR: ViewResult => {
      teacherQ.execute flatMap { teacherR: ViewResult => {
        roomQ.execute flatMap { roomR: ViewResult => {
          termQ.execute flatMap { termR: ViewResult =>
            labelQ.execute map { labelR: ViewResult =>
              (
                groupR.docs[docs.Group],
                teacherR.docs[docs.Teacher],
                roomR.docs[docs.Room],
                termR.docs[docs.Term],
                labelR.docs[docs.Label]
              )
            }
          }
        }}
      }}
    }}
  }

  def getConfigDefMap(
    configId: String
  ): Future[(
    Map[String, docs.Group], Map[String, docs.Teacher],
    Map[String, docs.Room], Map[String, docs.Term], Map[String, docs.Label]
  )] = {
    log.info(s"Getting definition for config $configId")

    val groupQ = couchClient.view("groups/by_config")
      .startkey(configId).endkey(configId).includeDocs
    val teacherQ = couchClient.view("teachers/by_config")
      .startkey(configId).endkey(configId).includeDocs
    val roomQ = couchClient.view("rooms/by_config").startkey(configId).endkey(configId).includeDocs
    val termQ = couchClient.view("terms/by_config").startkey(configId).endkey(configId).includeDocs
    val labelQ = couchClient.view("labels/by_config").startkey(configId).endkey(configId)
      .includeDocs

    groupQ.execute flatMap { groupR: ViewResult => {
      teacherQ.execute flatMap { teacherR: ViewResult => {
        roomQ.execute flatMap { roomR: ViewResult => {
          termQ.execute flatMap { termR: ViewResult =>
            labelQ.execute map { labelR: ViewResult =>
              (
                groupR.docs[docs.Group] map { x => (x._id, x)} toMap,
                teacherR.docs[docs.Teacher] map { x => (x._id, x)} toMap,
                roomR.docs[docs.Room] map { x => (x._id, x)} toMap,
                termR.docs[docs.Term] map { x => (x._id, x)} toMap,
                labelR.docs[docs.Label] map { x => (x._id, x)} toMap
              )
            }
          }
        }}
      }}
    }}
  }

  def getConfigInfo(configId: String): Future[scheduler.ConfigInfo] = {
    log.info(s"Getting config info for config $configId")
    getConfigDef(configId) map { case (groups, teachers, rooms, terms, labels) =>
      scheduler.ConfigInfo(
        configId,
        terms.map(_.asThrift),
        rooms.map(_.asThrift),
        teachers.map(_.asThrift),
        groups.map(_.asThrift)
      )
    }
  }

  def deleteConfig(configId: String): Future[Unit] = {
    log.info(s"Deleting config $configId")

    val docsQ = couchClient
      .view("utils/by_config")
      .startkey(configId)
      .endkey(configId)

    docsQ.execute flatMap { result: ViewResult =>
      couchClient.bulkDelete(result.docInfos) map { responses: Seq[CouchResponse] =>
        CouchResponse.logErrors(responses)
        ()
      }
    }
  }

  def getConfigs(): Future[Seq[scheduler.ConfigBasicInfo]] = {
    log.info(s"Getting configs")
    val configsQuery = couchClient
      .view("utils/by_type")
      .startkey("config")
      .endkey("config")
      .includeDocs

    configsQuery.execute map { result: ViewResult => result mapDocs {
      doc: docs.Config => scheduler.ConfigBasicInfo(doc._id, doc.year.toShort, doc.term)
    }}
  }

  def addConfigElement(
    configId: String,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    log.info(s"Adding elements for config $configId")

    val termDocs = terms map { docs.Term(configId, _) }
    val roomDocs = rooms map { docs.Room(configId, _) }
    val teacherDocs = teachers map { docs.Teacher(configId, _) }
    val groupDocs = groups map { docs.Group(configId, _) }

    val labelIds = (rooms.map(_.labels) ++ groups.map(_.labels)).foldLeft(Set.empty[String])({
      case (s: Set[String], labels: Seq[String]) => s ++ labels.toSet
    }) toSeq
    val labelDocs = labelIds map { docs.Label(configId, _) }

    getConfigDef(configId) flatMap { case (exGroups, exTeachers, exRooms, exTerms, exLabels) =>
      val valid = isValidConfig(
        configId,
        termDocs ++ exTerms,
        roomDocs ++ exRooms,
        teacherDocs ++ exTeachers,
        groupDocs ++ exGroups,
        labelDocs ++ exLabels
      )
      if (!valid._2) {
        throw scheduler.SchedulerException(s"Config is not valid: ${valid._1}")
      }

      val allDocs = termDocs ++ roomDocs ++ teacherDocs ++ groupDocs ++ labelDocs

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

  }

  def editConfigElement(
    configId: String,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    log.info(s"Editing elements for config $configId")

    getConfigDefMap(configId) flatMap { case (exGroups, exTeachers, exRooms, exTerms, exLabels) =>

      val ex = scheduler.SchedulerException("Id doesn't exist")

      val termDocs = terms map { x =>
        docs.Term(configId, x)
          .copy(_rev=exTerms.getOrElse(docs.Term.getCouchId(configId, x.id), throw ex)._rev)
      }
      val roomDocs = rooms map { x =>
        docs.Room(configId, x)
          .copy(_rev=exRooms.getOrElse(docs.Room.getCouchId(configId, x.id), throw ex)._rev)
      }
      val teacherDocs = teachers map { x =>
        docs.Teacher(configId, x)
          .copy(_rev=exTeachers.getOrElse(docs.Teacher.getCouchId(configId, x.id), throw ex)._rev)
      }
      val groupDocs = groups map { x =>
        docs.Group(configId, x)
          .copy(_rev=exGroups.getOrElse(docs.Group.getCouchId(configId, x.id), throw ex)._rev)
      }
      val labelIds = (rooms.map(_.labels) ++ groups.map(_.labels)).foldLeft(Set.empty[String])({
        case (s: Set[String], labels: Seq[String]) => s ++ labels.toSet
      }) toSeq
      val labelDocs = labelIds map { x =>
        docs.Label(configId, x)
          .copy(_rev=exLabels.get(docs.Label.getCouchId(configId, x)).map(_._rev).flatten)
      }

      val termIds = termDocs.map(_._id).toSet
      val roomIds = roomDocs.map(_._id).toSet
      val teacherIds = teacherDocs.map(_._id).toSet
      val groupIds = groupDocs.map(_._id).toSet

      val valid = isValidConfig(
        configId,
        termDocs ++ exTerms.values.toSeq.filter(x => !termIds.contains(x._id)),
        roomDocs ++ exRooms.values.toSeq.filter(x => !roomIds.contains(x._id)),
        teacherDocs ++ exTeachers.values.toSeq.filter(x => !teacherIds.contains(x._id)),
        groupDocs ++ exGroups.values.toSeq.filter(x => !groupIds.contains(x._id)),
        labelDocs ++ exLabels.values.toSeq.filter(x => !labelIds.contains(x._id))
      )
      if (!valid._2) {
        throw scheduler.SchedulerException(s"Config is not valid: ${valid._1}")
      }

      val allDocs = termDocs ++ roomDocs ++ teacherDocs ++ groupDocs ++ labelDocs

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
  }

  def removeConfigElement(
    configId: String, elementId: String, elementType: String
  ): Future[Unit] = {
    log.info(s"Removing element $elementId of type $elementType for config $configId")

    val clsMap = Map(
      "group" -> docs.Group,
      "term" -> docs.Term,
      "room" -> docs.Room,
      "teacher" -> docs.Teacher
    )
    val cls = clsMap.getOrElse(
      elementType, throw scheduler.SchedulerException("Wrong element type")
    )

    couchClient.get[docs.BaseDoc](cls.getCouchId(configId, elementId)) map { doc =>
      couchClient.delete[docs.BaseDoc](doc) map { _ => () }
    }
  }

  def copyConfigElements(
    toConfigId: String, fromConfigId: String, elementsType: String)
  : Future[Unit] = {
    log.info(s"Copying $elementsType from $fromConfigId to $toConfigId")

    if (!Set("room", "teacher", "term").contains(elementsType)) {
      throw scheduler.SchedulerException("Wrong elements type")
    }

    getConfigDef(toConfigId) flatMap { case (exGroups, exTeachers, exRooms, exTerms, exLabels) =>
      val teacherQ = couchClient.view("teachers/by_config")
        .startkey(fromConfigId).endkey(fromConfigId).includeDocs
      val roomQ = couchClient.view("rooms/by_config")
        .startkey(fromConfigId).endkey(fromConfigId).includeDocs
      val termQ = couchClient.view("terms/by_config")
        .startkey(fromConfigId).endkey(fromConfigId).includeDocs

      termQ.execute flatMap { termR: ViewResult =>
        val fromTerms = termR.docs[docs.Term]

        val newTeachersF = elementsType match {
          case "teacher" =>
            teacherQ.execute map { teacherR: ViewResult => teacherR.docs[docs.Teacher] }
          case _ => Future.value(Seq())
        }
        val newRoomsF = elementsType match {
          case "room" => roomQ.execute map { roomR: ViewResult => roomR.docs[docs.Room] }
          case _ => Future.value(Seq())
        }

        newTeachersF flatMap { fromTeachers: Seq[docs.Teacher] =>
          newRoomsF flatMap { fromRooms =>

            val newRooms = fromRooms map { _.editConfig(toConfigId) }
            val newTeachers = fromTeachers map { _.editConfig(toConfigId) }

            val newTermIds: Seq[String] = elementsType match {
              case "term" => fromTerms map { _.getRealId }
              case "teacher" => fromTeachers map { t: docs.Teacher => t.terms map {
                  x: String => docs.Term.getRealId(x)
                }} flatten
              case "room" => fromRooms map { r: docs.Room => r.terms map {
                  x: String => docs.Term.getRealId(x)
                }} flatten
            }
            val newTermIdsSet = newTermIds.toSeq
            val newTerms = fromTerms filter {
              x: docs.Term => newTermIdsSet.contains(x.getRealId)
            } map { x => x.editConfig(toConfigId) } filter { t =>
              val equalExTerms = exTerms.filter(_._id == t._id)
              equalExTerms match {
                case Seq(x) =>
                  if (x.isTheSame(t)) {
                    false
                  } else {
                    throw scheduler.SchedulerException(s"Term already exists: ${x.getRealId}")
                  }
                case Seq() => true
              }
            }

            val newLabels = newRooms.map(r => r.labels.map(docs.Label.getRealId)).flatten
              .filter(l => !exLabels.exists(_.getRealId == l))
              .map(l => docs.Label(toConfigId, l))

            val valid = isValidConfig(
              toConfigId, exTerms ++ newTerms, exRooms ++ newRooms, exTeachers ++ newTeachers,
              exGroups, exLabels ++ newLabels
            )
            if (!valid._2) {
              throw scheduler.SchedulerException(s"Config is not valid: ${valid._1}")
            }

            val elementExistsAlready =
              newTeachers.exists(t => exTeachers.exists(_._id == t._id)) ||
              newRooms.exists(r => exRooms.exists(_._id == r._id))

            if (elementExistsAlready) {
              throw scheduler.SchedulerException(
                s"Teacher or room already exists in config $toConfigId"
              )
            }

            val newDocs = newTeachers ++ newLabels ++ newRooms ++ newTerms
            couchClient.bulkAdd(newDocs).map { rseq: Seq[CouchResponse] => {
              CouchResponse.logErrors(rseq)
              ()
            }}

          }
        }

      }

    }
  }

  def importData(configId: String, data: String): Future[Unit] = {
    val doc = docs.ImportData(configId, data)

    couchClient.add[docs.ImportData](doc) flatMap { _ =>
      val config = serializers.Ii(configId, data).toConfigDef

      val valid = isValidConfig(
        configId, config.terms, config.rooms, config.teachers,
        config.groups, config.labels
      )
      if (!valid._2) {
        throw scheduler.SchedulerException(s"Config is not valid: ${valid._1}")
      }

      couchClient.bulkAdd(config.allDocs) map { rseq: Seq[CouchResponse] => {
        CouchResponse.logErrors(rseq)
        ()
      }}

    }
  }

}
