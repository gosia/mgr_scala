package com.mgr.utils.couch

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.Http
import net.liftweb.json

import com.mgr.utils.logging.Logging

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
) {
  def errorMsg: Option[String] = {
    error.isDefined match {
      case true => Some(s"Error: ${error.get}, reason: ${reason.getOrElse("not given")}")
      case false => None
    }
  }
}

final case class CouchException(m: String) extends Exception

case class CouchResult[T <: Document](response: CouchResponse, doc: T)

case class DocInfo(_id: String, _rev: String)

trait Document {

  val _id: String
  val _rev: Option[String]
  val `type`: String

}

final case class ViewRow(
  id: String,
  key: json.JValue,
  value: json.JValue,
  doc: json.JValue
)

final case class ViewResult(
  total_rows: Int,
  offset: Int,
  rows: Seq[ViewRow]
) {

  def mapDocs[DocType: Manifest, T](f: DocType => T): Seq[T] = {
    rows map { _.doc.extract[DocType] } map f
  }

  def ids: Seq[String] = rows.map(_.id)

  def docs[DocType: Manifest]: Seq[DocType] = rows map { _.doc.extract[DocType] }

}
