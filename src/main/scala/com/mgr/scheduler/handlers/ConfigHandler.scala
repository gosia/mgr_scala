package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.thrift.scheduler.ValidationException
import com.mgr.utils.couch.CouchResponse
import com.mgr.utils.couch.ViewResult
import com.mgr.utils.logging.Logging

object ConfigHandler extends Logging  with Couch {

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

    def validateUniqIds(xs: Seq[String]): (Option[String], Boolean) = {
      val duplicates: Seq[String] =
        xs.groupBy(identity).collect { case (x, ys) if ys.size > 1 => x } toSeq

      duplicates match {
        case Seq() => (None, true)
        case _ => (Some(s"Id grup (${duplicates.mkString(", ")}) nie są unikalne"), false)
      }
    }

    val validated: Seq[(Option[String], Boolean)] =
      terms.map(_.isValid) ++
      rooms.map(_.isValid(termIds, labelIds)) ++
      teachers.map(_.isValid(termIds)) ++
      groups.map(_.isValid(termIds, labelIds, groupIds, teacherIds)) ++
      Seq(
        validateUniqIds(groups.map(_._id)),
        validateUniqIds(teachers.map(_._id)),
        validateUniqIds(rooms.map(_._id)),
        validateUniqIds(terms.map(_._id))
      )

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
    info: scheduler.ConfigCreationInfo,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    val id = info.id
    log.info(s"Adding config $id")

    couchClient.exists(id) map { exists =>

      if (exists) {
        throw scheduler.ValidationException("Przydział o podanym id już istnieje")
      }

      val termDocs = terms map { docs.Term(id, _) }
      val roomDocs = rooms map { docs.Room(id, _) }
      val teacherDocs = teachers map { docs.Teacher(id, _) }
      val groupDocs = groups map { docs.Group(id, _) }
      val configDoc = docs.Config(_id=id, year=info.year, term=info.term.name.toLowerCase)

      val labelIds = (rooms.map(_.labels) ++ groups.map(_.labels)).foldLeft(Set.empty[String])({
        case (s: Set[String], labels: Seq[String]) => s ++ labels.toSet
      }) toSeq
      val labelDocs = labelIds map { docs.Label(id, _) }

      val valid = isValidConfig(id, termDocs, roomDocs, teacherDocs, groupDocs, labelDocs)
      if (!valid._2) {
        throw scheduler.ValidationException(s"Przydział nie jest poprawny: ${valid._1}")
      }

      val allDocs = termDocs ++ roomDocs ++ teacherDocs ++ groupDocs ++ labelDocs ++ Seq(configDoc)

      couchClient.bulkAdd(allDocs).map { rseq: Seq[CouchResponse] => {
        CouchResponse.logErrors(rseq)
        ()
      }}

    }
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

  def getConfig(configId: String): Future[scheduler.Config] = {
    log.info(s"Getting config info for config $configId")

    couchClient.get[docs.Config](configId) flatMap {
      case None => throw ValidationException(s"Przydział $configId nie istnieje")
      case Some(config) =>
        getConfigDef(configId) map { case (groups, teachers, rooms, terms, labels) =>
          scheduler.Config(
            scheduler.ConfigBasicInfo(
              configId, config.year.toShort, scheduler.TermType.valueOf(config.term).get,
              config.file
            ),
            terms.map(_.asThrift),
            rooms.map(_.asThrift),
            teachers.map(_.asThrift),
            groups.map(_.asThrift)
          )
        }
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
      doc: docs.Config => scheduler.ConfigBasicInfo(
        doc._id, doc.year.toShort, scheduler.TermType.valueOf(doc.term).get, doc.file
      )
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

    couchClient.get[docs.Config](configId) flatMap {
      case None => throw scheduler.ValidationException(s"Przydział $configId nie istnieje")
      case Some(config) =>

        val termDocs = terms map {
          docs.Term(configId, _)
        }
        val roomDocs = rooms map {
          docs.Room(configId, _)
        }
        val teacherDocs = teachers map {
          docs.Teacher(configId, _)
        }
        val groupDocs = groups map {
          docs.Group(configId, _)
        }

        val labelIds = (rooms.map(_.labels) ++ groups.map(_.labels)).foldLeft(Set.empty[String])({
          case (s: Set[String], labels: Seq[String]) => s ++ labels.toSet
        }) toSeq
        val labelDocs = labelIds map {
          docs.Label(configId, _)
        }

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
            throw scheduler.ValidationException(s"Przydział nie jest poprawny: ${valid._1}")
          }

          val updateFileF: Future[Unit] = {
            config.file match {
              case None => Future.Unit
              case Some(fileId) => FileHandler.addElements(fileId, config, teachers, groups)
            }
          }

          val otherDocs = {
            config.file match {
              case None => Seq()
              case Some(fileId) =>
                // TODO(gosia): add validation?
                // TODO(gosia): add labels?
                val otherConfigId = {
                  configId match {
                    case c if configId.endsWith("-1") => s"$fileId-2"
                    case _ => s"$fileId-1"
                  }
                }
                val otherTermDocs = terms map {
                  docs.Term(otherConfigId, _)
                }
                val otherRoomDocs = rooms map {
                  docs.Room(otherConfigId, _)
                }
                val otherTeacherDocs = teachers map {
                  docs.Teacher(otherConfigId, _)
                }
                otherTermDocs ++ otherRoomDocs ++ otherTeacherDocs
            }
          }

          updateFileF flatMap { _ =>
            val allDocs = termDocs ++ roomDocs ++ teacherDocs ++ groupDocs ++ labelDocs ++ otherDocs

            couchClient.bulkAdd(allDocs).map { rseq: Seq[CouchResponse] => {
              CouchResponse.logErrors(rseq)
              ()
            } }
          }
        }
    }

  }

  private def editSecondConfigElement(
    config: docs.Config,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher]
  ) = {
    config.file match {
      case None => Future.value(Seq())
      case Some(fileId) =>
        val otherConfigId = {
          config._id match {
            case c if config._id.endsWith("-1") => s"$fileId-2"
            case _ => s"$fileId-1"
          }
        }
        val otherTermDocsF: Future[Seq[docs.Term]] = Future.collect {
          terms map { x =>
            val id = docs.Term.getCouchId(otherConfigId, x.id)
            couchClient.get[docs.Term](id) map {
              case None => throw scheduler.SchedulerException("sth is wrong")
              case Some(doc) =>
                docs.Term(otherConfigId, x).copy(
                  _rev = doc._rev
                )
            }
          }
        }
        val otherRoomDocsF: Future[Seq[docs.Room]] = Future.collect {
          rooms map { x =>
            val id = docs.Room.getCouchId(otherConfigId, x.id)
            couchClient.get[docs.Room](id) map {
              case None => throw scheduler.SchedulerException("sth is wrong")
              case Some(doc) =>
                docs.Room(otherConfigId, x).copy(
                  _rev = doc._rev,
                  terms = doc.terms
                )
            }
          }
        }
        val otherTeacherDocsF: Future[Seq[docs.Teacher]] = Future.collect {
          teachers map { x =>
            val id = docs.Teacher.getCouchId(otherConfigId, x.id)
            couchClient.get[docs.Teacher](id) map {
              case None => throw scheduler.SchedulerException("sth is wrong")
              case Some(doc) =>
                docs.Teacher(otherConfigId, x).copy(
                  _rev = doc._rev,
                  terms = doc.terms
                )
            }
          }
        }
        otherTermDocsF flatMap { otherTermsDocs =>
          otherRoomDocsF flatMap { otherRoomDocs =>
            otherTeacherDocsF map { otherTeacherDocs =>
              otherTermsDocs ++ otherRoomDocs ++ otherTeacherDocs
            }
          }
        }
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

    couchClient.get[docs.Config](configId) flatMap {
      case None => throw scheduler.ValidationException(s"Przydział $configId nie istnieje")
      case Some(config) =>

        getConfigDefMap(
          configId
        ) flatMap { case (exGroups, exTeachers, exRooms, exTerms, exLabels) =>

          val ex = scheduler.ValidationException("Id elementu nie istnieje")

          val termDocs = terms map { x =>
            docs.Term(configId, x)
              .copy(_rev = exTerms.getOrElse(docs.Term.getCouchId(configId, x.id), throw ex)._rev)
          }
          val roomDocs = rooms map { x =>
            docs.Room(configId, x)
              .copy(_rev = exRooms.getOrElse(docs.Room.getCouchId(configId, x.id), throw ex)._rev)
          }
          val teacherDocs = teachers map { x =>
            docs.Teacher(configId, x)
              .copy(
                _rev = exTeachers.getOrElse(docs.Teacher.getCouchId(configId, x.id), throw ex)._rev
              )
          }
          val groupDocs = groups map { x =>
            docs.Group(configId, x)
              .copy(_rev = exGroups.getOrElse(docs.Group.getCouchId(configId, x.id), throw ex)._rev)
          }
          val labelIds = (rooms.map(_.labels) ++ groups.map(_.labels)).foldLeft(Set.empty[String])({
            case (s: Set[String], labels: Seq[String]) => s ++ labels.toSet
          }) toSeq
          val labelDocs = labelIds map { x =>
            docs.Label(configId, x)
              .copy(_rev = exLabels.get(docs.Label.getCouchId(configId, x)).map(_._rev).flatten)
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
            throw scheduler.ValidationException(s"Przydział nie jest poprawny: ${valid._1}")
          }

          val updateFileF: Future[Unit] = {
            config.file match {
              case None => Future.Unit
              case Some(fileId) => FileHandler.editElements(fileId, config, teachers, groups)
            }
          }

          updateFileF flatMap { _ =>
            editSecondConfigElement(config, terms, rooms, teachers) flatMap { otherDocs =>
              val allDocs =
                termDocs ++ roomDocs ++ teacherDocs ++ groupDocs ++ labelDocs ++ otherDocs

              couchClient.bulkAdd(allDocs).map { rseq: Seq[CouchResponse] => {
                CouchResponse.logErrors(rseq)
                ()
              } }
            }
          }
        }
    }
  }

  def removeTeacherRelatedObjects(
    configId: String, elementId: String
  ): Future[Unit] = {

    val groupsQ = couchClient.view("groups/related_by_teacher")
      .startkey(Seq(configId, elementId))
      .endkey(Seq(configId, elementId))
      .includeDocs

    groupsQ.execute flatMap { viewResults: ViewResult =>
      val groups = viewResults.docs[docs.Group]

      val newGroups = groups.map({ group =>
        group.copy(teachers = group.teachers.filter(_ != elementId))
      })

      couchClient.bulkAdd(newGroups) map { _ => () }
    }
  }

  def removeGroupRelatedObjects(
    configId: String, elementId: String
  ): Future[Unit] = {

    val groupsQ = couchClient.view("groups/related_by_group")
      .startkey(Seq(configId, elementId))
      .endkey(Seq(configId, elementId))
      .includeDocs

    groupsQ.execute flatMap { viewResults: ViewResult =>
      val groups = viewResults.docs[docs.Group]

      val newGroups = groups.map({ group => group.copy(
        same_term_groups = group.same_term_groups.filter(_ != elementId),
        diff_term_groups = group.diff_term_groups.filter(_ != elementId)
      )})

      couchClient.bulkAdd(newGroups) map { _ => () }
    }
  }

  def removeTermRelatedObjects(
    configId: String, elementId: String
  ): Future[Unit] = {

    val groupsQ = couchClient.view("groups/related_by_term")
      .startkey(Seq(configId, elementId))
      .endkey(Seq(configId, elementId))
      .includeDocs

    val roomsQ = couchClient.view("rooms/related_by_term")
      .startkey(Seq(configId, elementId))
      .endkey(Seq(configId, elementId))
      .includeDocs

    val teachersQ = couchClient.view("teachers/related_by_term")
      .startkey(Seq(configId, elementId))
      .endkey(Seq(configId, elementId))
      .includeDocs

    val groupsF = groupsQ.execute map { viewResults: ViewResult =>
      val xs = viewResults.docs[docs.Group]
      xs.map({ x => x.copy(terms = x.terms.filter(_ != elementId))})
    }

    val roomsF = roomsQ.execute map { viewResults: ViewResult =>
      val xs = viewResults.docs[docs.Room]
      xs.map({ x => x.copy(terms = x.terms.filter(_ != elementId))})
    }

    val teachersF = teachersQ.execute map { viewResults: ViewResult =>
      val xs = viewResults.docs[docs.Teacher]
      xs.map({ x => x.copy(terms = x.terms.filter(_ != elementId))})
    }

    groupsF flatMap { newGroups =>
      roomsF flatMap { newRooms =>
        teachersF flatMap { newTeachers =>
          couchClient.bulkAdd(newTeachers ++ newRooms ++ newGroups) map { _ => () }
        }
      }
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
    val relatedFMap: Map[String, (String, String) => Future[Unit]] = Map(
      "group" -> removeGroupRelatedObjects,
      "term" -> removeTermRelatedObjects,
      "room" -> ((configId: String, elementId: String) => Future.Unit),
      "teacher" -> removeTeacherRelatedObjects
    )
    val cls = clsMap.getOrElse(
      elementType, throw scheduler.ValidationException(s"Niepoprawny typ elementu $elementType")
    )

    couchClient.get[docs.BaseDoc](cls.getCouchId(configId, elementId)) flatMap {
      case None => throw scheduler.ValidationException(s"Element $elementId nie istnieje")
      case Some(doc) =>
        relatedFMap(elementType)(configId, doc._id) flatMap { _ =>
          couchClient.delete[docs.BaseDoc](doc) map { _ => () }
        }
    }
  }

  def copyConfigElements(
    toConfigId: String, fromConfigId: String, elementsType: String)
  : Future[Unit] = {
    log.info(s"Copying $elementsType from $fromConfigId to $toConfigId")

    if (!Set("room", "teacher", "term").contains(elementsType)) {
      throw scheduler.ValidationException(s"Niepoprawny typ elementu $elementsType")
    }

    couchClient.exists(toConfigId) map { exists =>

      if (!exists) {
        throw scheduler.ValidationException(s"Przydział $toConfigId nie istnieje")
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
              teacherQ.execute map { teacherR: ViewResult => teacherR.docs[docs.Teacher]}
            case _ => Future.value(Seq())
          }
          val newRoomsF = elementsType match {
            case "room" => roomQ.execute map { roomR: ViewResult => roomR.docs[docs.Room]}
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
                } } flatten
                case "room" => fromRooms map { r: docs.Room => r.terms map {
                  x: String => docs.Term.getRealId(x)
                } } flatten
              }
              val newTermIdsSet = newTermIds.toSeq
              val newTerms = fromTerms filter {
                x: docs.Term => newTermIdsSet.contains(x.getRealId)
              } map { x => x.editConfig(toConfigId)} filter { t =>
                val equalExTerms = exTerms.filter(_._id == t._id)
                equalExTerms match {
                  case Seq(x) =>
                    if (x.isTheSame(t)) {
                      false
                    } else {
                      throw scheduler.ValidationException(s"Termin już istnieje: ${x.getRealId}")
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
                throw scheduler.ValidationException(s"Przydział nie jest poprawny: ${valid._1}")
              }

              val elementExistsAlready =
                newTeachers.exists(t => exTeachers.exists(_._id == t._id)) ||
                  newRooms.exists(r => exRooms.exists(_._id == r._id))

              if (elementExistsAlready) {
                throw scheduler.SchedulerException(
                  s"Nauczyciel lub pokój już istnieje w przydziale $toConfigId"
                )
              }

              val newDocs = newTeachers ++ newLabels ++ newRooms ++ newTerms
              couchClient.bulkAdd(newDocs).map { rseq: Seq[CouchResponse] => {
                CouchResponse.logErrors(rseq)
                ()
              } }
            }
          }
        }
      }
    }
  }

}
