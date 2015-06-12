package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.datastructures
import com.mgr.scheduler.datastructures.Implicits._
import com.mgr.scheduler.docs
import com.mgr.scheduler.serializers
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.CouchResponse
import com.mgr.utils.couch.ViewResult
import com.mgr.utils.logging.Logging

object FileHandler extends Logging with Couch {
  val DEFAULT_FILE = "system:default_content"

  def create(info: scheduler.FileCreationInfo): Future[scheduler.File] = {
    log.info(s"Creating file ${info.id}")

    couchClient.get[docs.File](DEFAULT_FILE) flatMap {
      case None => throw scheduler.SchedulerException(s"Brak pliku domyślnego w bazie danych")
      case Some(default) =>
        val file = docs.File(info, default.content)
        couchClient.add[docs.File](file) map { _ => file.toThrift }
    }
  }

  def delete(fileId: String): Future[Unit] = {
    log.info(s"Deleting file $fileId")

    if (fileId == DEFAULT_FILE) {
      throw scheduler.SchedulerException("Nie można usunąć domyślnego pliku!")
    }

    couchClient.get[docs.File](fileId) flatMap {
      case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
      case Some(file) =>
        file.linked match {
          case false => couchClient.delete[docs.File](file) map { _ => () }
          case true =>
            val configs: docs.FileConfigs = file.configs.getOrElse(
              throw scheduler.SchedulerException(s"Sth is wrong")
            )
            ConfigHandler.deleteConfig(configs.configId1) flatMap { _ =>
              ConfigHandler.deleteConfig(configs.configId2) flatMap { _ =>
                couchClient.delete[docs.File](file) map { _ => () }
              }
            }
        }
    }
  }
  def get(fileId: String): Future[scheduler.File] = {
    log.info(s"Getting file $fileId")
    couchClient.get[docs.File](fileId) map {
      case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
      case Some(file) => file.toThrift
    }
  }

  def list(): Future[Seq[scheduler.FileBasicInfo]] = {
    log.info(s"Listing files")

    val query = couchClient
      .view("files/basic_info")
      .includeDocs

    query.execute map { result: ViewResult =>
      result.mapValues[docs.FileInfoView, scheduler.FileBasicInfo]
        { _.toThrift } filter { _.id != DEFAULT_FILE }
    }
  }

  def saveRelated(doc: docs.File, file: datastructures.File): Future[Unit] = {
    doc.linked match {
      case false => Future.Unit
      case true =>
        serializers.Ii(doc).toFileDef flatMap { currentFile =>
          val docsToDelete = currentFile.getNewDeleted(file)
          val newFile = currentFile.setNew(file)

          val part1 = newFile.config1.allDocs
          val part2 = newFile.config2.allDocs

          couchClient.bulkAdd(part1) flatMap { rseq: Seq[CouchResponse] => {
            CouchResponse.logErrors(rseq)

            couchClient.bulkAdd(part2) flatMap { rseq: Seq[CouchResponse] => {
              CouchResponse.logErrors(rseq)

              val deleteRelatedF = Future.collect {
                docsToDelete.map { doc =>
                  ConfigHandler.removeTermRelatedObjects(doc.config_id, doc._id)
                }
              }

              deleteRelatedF flatMap { _ =>
                couchClient.bulkDelete(docsToDelete) map { rseq: Seq[CouchResponse] =>
                  CouchResponse.logErrors(rseq)
                  ()
                }
              }
            }}

          }}
        }
    }
  }

  def save(fileId: String, content: String): Future[Unit] = {
    log.info(s"Saving file $fileId")

    serializers.Ii(fileId, content).toFileDef flatMap { file =>

      val valid = file.isValid
      if (!valid._2) {
        throw scheduler.ValidationException(s"Plik nie jest poprawny: ${valid._1}")
      }

      couchClient.get[docs.File](fileId) flatMap {
        case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
        case Some(doc) =>
          saveRelated(doc, file) flatMap { _ =>
            val newFile = doc.copy(content = content)
            couchClient.update[docs.File](newFile) map { _ => ()}
          }
      }
    }
  }

  def link(fileId: String): Future[scheduler.FileBasicInfo] = {
    log.info(s"Linking file $fileId")

    couchClient.get[docs.File](fileId) flatMap {
      case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
      case Some(doc) =>
        serializers.Ii(doc).toFileDef flatMap { file =>

          val config1 = docs.Config(
            _id = file.config1.configId,
            year = doc.year,
            term = "winter",
            file = Some(file.id)
          )

          val config2 = docs.Config(
            _id = file.config2.configId,
            year = doc.year,
            term = "summer",
            file = Some(file.id)
          )

          val part1 = Seq(config1) ++ file.config1.allDocs
          val part2 = Seq(config2) ++ file.config2.allDocs

          couchClient.bulkAdd(part1) flatMap { rseq: Seq[CouchResponse] => {
            CouchResponse.logErrors(rseq)

            couchClient.bulkAdd(part2) flatMap { rseq: Seq[CouchResponse] => {
              CouchResponse.logErrors(rseq)

              val newDoc = doc.link(file.config1.configId, file.config2.configId)
              couchClient.update[docs.File](newDoc) map { _ =>
                newDoc.toThrift.info
              }

            }}

          }}
        }

    }

  }

  def addElements(
    fileId: String,
    config: docs.Config,
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    couchClient.get[docs.File](fileId) flatMap {
      case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
      case Some(doc) =>
        val ii = serializers.Ii(doc)

        ii.toFileDef flatMap { file =>

          val fileWithTeachers = teachers.foldLeft(file) { case (f, t) => f.addTeacher(t) }
          val fileWithGroups = groups.foldLeft(fileWithTeachers) {
            case (f, g) => f.addGroup(g, config)
          }

          val newContent = new serializers.IiLinear {}.fromLineSeq(fileWithGroups.lines)
          val newFile = doc.copy(content = newContent)

          couchClient.update[docs.File](newFile) map { _ => ()}
        }
    }
  }

  def editElements(
    fileId: String,
    config: docs.Config,
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    couchClient.get[docs.File](fileId) flatMap {
      case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
      case Some(doc) =>
        val ii = serializers.Ii(doc)
        ii.toFileDef flatMap { file =>

          val linesWithTeachers = teachers.foldLeft(file.lines) { case (lines, teacher) =>
            lines.mapTeacher({ t: docs.Teacher => if (t.getRealId == teacher.id) {
              docs.Teacher(t.config_id, teacher)
            } else {
              t
            }})
          }
          val linesWithGroups = groups.foldLeft(linesWithTeachers) { case (lines, group) =>
            lines.mapGroup({ g: docs.Group => if (g.getRealId == group.id) {
              docs.Group(g.config_id, group)
            } else {
              g
            }})
          }

          val newContent = new serializers.IiLinear {}.fromLineSeq(linesWithGroups)
          val newFile = doc.copy(content = newContent)

          couchClient.update[docs.File](newFile) map { _ => ()}
        }
    }
  }

  def removeElement(fileId: String, elementId: String, elementType: String): Future[Unit] = {
    couchClient.get[docs.File](fileId) flatMap {
      case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
      case Some(file) =>
        elementType match {
          case e if e == "group" || e == "teacher" =>
            val IiLinearO = new serializers.IiLinear {}
            val lineSeq = IiLinearO.toLineSeq(fileId, file.content)
            val newLineSeq = lineSeq.removeElement(elementId, elementType)
            val newContent = IiLinearO.fromLineSeq(newLineSeq)
            val newFile = file.copy(content = newContent)

            couchClient.update[docs.File](newFile) map { _ => () }

          case e if e == "room" || e == "term" => Future.Unit
        }
    }
  }

}
