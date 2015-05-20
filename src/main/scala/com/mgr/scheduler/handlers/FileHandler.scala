package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.docs
import com.mgr.scheduler.serializers
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.ViewResult
import com.mgr.utils.logging.Logging

object FileHandler extends Logging with Couch {
  val DEFAULT_FILE = "system:default_content"

  def create(info: scheduler.FileCreationInfo): Future[scheduler.File] = {
    log.info(s"Creating file ${info.id}")

    couchClient.get[docs.File](DEFAULT_FILE) flatMap { default =>
      val file = docs.File(info, default.content)
      couchClient.add[docs.File](file) map { _ =>
        file.toThrift
      }
    }
  }

  def delete(fileId: String): Future[Unit] = {
    log.info(s"Deleting file $fileId")

    if (fileId == DEFAULT_FILE) {
      throw scheduler.SchedulerException("Can't delete default file")
    }

    couchClient.get[docs.File](fileId) flatMap { file =>
      file.linked match {
        case true => throw scheduler.SchedulerException("File is linked, can't delete.")
        case false =>
          couchClient.delete[docs.File](file) map { _ => () }
      }
    }
  }
  def get(fileId: String): Future[scheduler.File] = {
    log.info(s"Getting file $fileId")
    couchClient.get[docs.File](fileId) map { file => file.toThrift }
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
      throw scheduler.SchedulerException(s"File is not valid: ${valid._1}")
    }

    couchClient.get[docs.File](fileId) flatMap { doc =>
      val newFile = doc.copy(content = content)
      couchClient.update[docs.File](newFile) map { _ => () }
    }
  }

}
