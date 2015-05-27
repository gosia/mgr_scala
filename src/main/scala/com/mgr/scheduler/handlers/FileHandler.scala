package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
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
          case true => throw scheduler.SchedulerException("File is linked, can't delete.")
          case false =>
            couchClient.delete[docs.File](file) map { _ => () }
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

  def save(fileId: String, content: String): Future[Unit] = {
    log.info(s"Saving file $fileId")

    val file = serializers.Ii(fileId, content).toFileDef
    val valid = file.isValid
    if (!valid._2) {
      throw scheduler.ValidationException(s"Plik nie jest poprawny: ${valid._1}")
    }

    couchClient.get[docs.File](fileId) flatMap {
      case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
      case Some(doc) =>
        val newFile = doc.copy(content = content)
        couchClient.update[docs.File](newFile) map { _ => () }
    }
  }

  def link(fileId: String): Future[scheduler.FileBasicInfo] = {
    log.info(s"Linking file $fileId")

    couchClient.get[docs.File](fileId) flatMap {
      case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
      case Some(doc) =>
        val file = serializers.Ii(fileId, doc.content).toFileDef

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

  def addElements(
    fileId: String,
    config: docs.Config,
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    couchClient.get[docs.File](fileId) flatMap {
      case None => throw scheduler.ValidationException(s"Plik $fileId nie istnieje")
      case Some(doc) =>
        val ii = serializers.Ii(fileId, doc.content)
        val file = ii.toFileDef

        val fileWithTeachers = teachers.foldLeft(file) { case (f, t) => f.addTeacher(t) }
        val fileWithGroups = groups.foldLeft(fileWithTeachers) {
          case (f, g) => f.addGroup(g, config)
        }

        val newContent = ii.fromLineSeq(fileWithGroups.lines)
        val newFile = doc.copy(content = newContent)

        couchClient.update[docs.File](newFile) map { _ => () }
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
        val ii = serializers.Ii(fileId, doc.content)
        val file = ii.toFileDef

        val linesWithTeachers = teachers.foldLeft(file.lines) { case (lines, teacher) =>
          lines.mapTeacher({ t: docs.Teacher => if (t.getRealId == teacher.id) {
            docs.Teacher(t.config_id, teacher)
          } else {
            t
          } })
        }
        val linesWithGroups = groups.foldLeft(linesWithTeachers) { case (lines, group) =>
          lines.mapGroup({ g: docs.Group => if (g.getRealId == group.id) {
            docs.Group(g.config_id, group)
          } else {
            g
          } })
        }

        val newContent = ii.fromLineSeq(linesWithGroups)
        val newFile = doc.copy(content = newContent)

        couchClient.update[docs.File](newFile) map { _ => () }
    }
  }

}
