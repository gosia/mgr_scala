package com.mgr.scheduler.handlers

import com.twitter.util.Future
import net.liftweb.json.JsonAST.JInt
import net.liftweb.json.JsonAST.JString

import com.mgr.scheduler.Couch
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.CouchResponse
import com.mgr.utils.couch.ReducedViewResult
import com.mgr.utils.couch.ViewResult
import com.mgr.utils.logging.Logging

object VoteHandler extends Logging  with Couch {

  def set(configId: String, votes: Map[String, Map[String, Short]]): Future[Unit] = {
    VoteHandler.delete(configId) flatMap { _ =>
      val allDocs = votes.keys.toSeq.map(user => docs.UserVote(configId, votes, user))

      couchClient.bulkAdd(allDocs) map { responses =>
        CouchResponse.logErrors(responses)
        ()
      }
    }
  }

  def get(configId: String): Future[scheduler.UsersVotes] = {
    val votesQ = couchClient.view("votes/by_config")
      .startkey(configId)
      .endkey(configId)
      .reduce(false)
      .includeDocs

    votesQ.execute map { viewResults: ViewResult =>
      docs.UserVote.toThrift(configId, viewResults.docs[docs.UserVote])
    }
  }

  def list(): Future[Seq[scheduler.UsersVotes]] = {
    val votesQ = couchClient.view("votes/by_config")
      .reduce(true)
      .groupLevel(1)

    votesQ.executeReduced map { viewResults: ReducedViewResult =>
      viewResults.rows.map(
        x => scheduler.UsersVotes(
          x.key.asInstanceOf[JString].values, x.value.asInstanceOf[JInt].values.toShort, Map()
        )
      )
    }
  }

  def delete(configId: String): Future[Unit] = {
    couchClient.exists(configId) flatMap {
      case false => throw scheduler.ValidationException("Przydział o podanym id nie istnieje")
      case true =>
        val votesQ = couchClient.view("votes/by_config")
          .startkey(configId)
          .endkey(configId)
          .reduce(false)
          .includeDocs

        votesQ.execute flatMap { viewResults: ViewResult =>
          val docsToRemove = viewResults.docs[docs.UserVote]

          docsToRemove.size match {
            case 0 => Future.Unit
            case _ =>
              couchClient.bulkDelete[docs.UserVote](docsToRemove) map { responses =>
                CouchResponse.logErrors(responses)
                ()
              }
          }
        }
    }
  }

}
