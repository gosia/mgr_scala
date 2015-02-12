package com.mgr.utils.couch

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.Http

final case class BulkDoc[T <: Document](docs: Seq[T])

case class CouchConfig(
  host: String,
  port: Int,
  db: String,
  retryDelay: Int = 3
){

  lazy val couchBuilder = ClientBuilder()
    .codec(Http())
    .hosts(s"$host:$port")
    .hostConnectionLimit(1)

}

case class CouchResponse(
  id: String,
  error: Option[String],
  ok: Option[Boolean],
  reason: Option[String],
  rev: Option[String]
)

final case class CouchException(m: String) extends Exception

case class CouchResult[T <: Document](response: CouchResponse, doc: T)

case class DocInfo(_id: String, _rev: String)

trait Document {

  val _id: String
  val _rev: Option[String]
  val `type`: String

}
