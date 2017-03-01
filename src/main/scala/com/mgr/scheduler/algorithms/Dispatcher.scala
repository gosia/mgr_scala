package com.mgr.scheduler.algorithms

import com.twitter.conversions.time._
import com.twitter.util.Future
import com.twitter.util.JavaTimer

import com.mgr.scheduler.docs
import com.mgr.scheduler.handlers.RatingHandler
import com.mgr.thrift.scheduler

object Dispatcher extends Base {

  def getAlgorithm(task: docs.Task): Future[Base] = {
    task.algorithm match {
      case x if x == scheduler.Algorithm.Random.name.toLowerCase => Future.value(Random())
      case x if x == scheduler.Algorithm.RandomOrderedGroups.name.toLowerCase =>
        Future.value(RandomOrderedGroups())
      case x if x == scheduler.Algorithm.DecideWithRatingFunction.name.toLowerCase =>
        couchClient.get[docs.Rating](docs.Rating.getCouchId("default")) flatMap {
          case None => throw scheduler.SchedulerException("no default rating schema")
          case Some(rating) =>
            couchClient.get[docs.StudentVotes](docs.StudentVotes.getCouchId(task.config_id)) map {
              case None => DecideWithRatingFunction(rating, Seq())
              case Some(studentVotes) => DecideWithRatingFunction(rating, studentVotes.votes)
            }
        }
    }
  }

  def start(task: docs.Task): Future[Unit] = {
    getAlgorithm(task) flatMap { algorithm: Base =>
      algorithm.start(task) flatMap { _ =>
        Future.sleep(2.seconds)(new JavaTimer(true)) map { _ =>
          RatingHandler.countRatingHelper(task._id)
        }
      }
    }
  }
}
