package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.ViewResult
import com.mgr.utils.logging.Logging

object VoteHandler extends Logging  with Couch {

  def set(configId: String, votes: Seq[scheduler.StudentVote]): Future[Unit] = {
    log.info(s"VOTE: Set for config $configId")

    val id = docs.StudentVotes.getCouchId(configId)

    couchClient.get[docs.StudentVotes](id) flatMap {
      case None =>
        val newDoc = docs.StudentVotes(configId, votes)
        couchClient.update[docs.StudentVotes](newDoc) map { _ => () }
      case Some(doc) =>
        val newDoc = docs.StudentVotes(configId, votes).copy(_rev=doc._rev)
        couchClient.update[docs.StudentVotes](newDoc) map { _ => () }
    }
  }

  def get(configId: String): Future[scheduler.StudentVotes] = {
    log.info(s"VOTE: Get for config $configId")

    val id = docs.StudentVotes.getCouchId(configId)

    couchClient.get[docs.StudentVotes](id) map {
      case None => throw scheduler.ValidationException(s"Głosowanie dla $configId nie istnieje")
      case Some(doc) => doc.toThrift
    }
  }

  def list(): Future[Seq[scheduler.StudentVotes]] = {
    log.info(s"VOTE: List")

    val votesQ = couchClient.view("utils/by_type")
      .startkey(docs.StudentVotes.`type`)
      .endkey(docs.StudentVotes.`type`)
      .reduce(false)
      .includeDocs

    votesQ.execute map { result: ViewResult => result mapDocs {
      doc: docs.StudentVotes => doc.toThrift
    }}
  }

  def delete(configId: String): Future[Unit] = {
    log.info(s"VOTE: Delete for config $configId")

    val id = docs.StudentVotes.getCouchId(configId)

    couchClient.get[docs.StudentVotes](id) flatMap {
      case None => throw scheduler.ValidationException(s"Głosowanie dla $configId nie istnieje")
      case Some(doc) => couchClient.delete[docs.StudentVotes](doc) map { _ => () }
    }
  }

}
