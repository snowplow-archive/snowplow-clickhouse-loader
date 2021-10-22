package com.snowplowanalytics.snowplow.clickhouse.loader

import io.circe.Json
import io.circe.JsonObject

import scala.concurrent.duration._

import fs2.{ Stream, Pipe, Chunk }
import fs2.text.utf8Encode

import org.http4s.{ Request, Uri, Method, Response }
import org.http4s.client.Client

import cats.implicits._
import cats.effect.{ Timer, Sync, Concurrent }
import cats.effect.concurrent.Ref

import com.snowplowanalytics.snowplow.analytics.scalasdk.Event

import com.snowplowanalytics.iglu.schemaddl.StringUtils.getTableName
import com.snowplowanalytics.iglu.core.{ SchemaKey, SchemaMap }

import com.snowplowanalytics.snowplow.clickhouse.loader.Transformation.TableState


object Loader {
  // Execute arbitrary statement
  def execute[F[_]: Sync](client: Client[F], output: LoaderConfig.Good)(statement: String): F[String] = {
    val req = build[F](output)(statement)
    client.run(req).use(decode[F])
  }

  def getColumnName(schemaKey: SchemaKey): String =
    getTableName(SchemaMap(schemaKey))

  def transform(event: Event): String =
    Json.fromFields(event.atomic).mapObject { obj =>
      val fields = obj.toMap.map {
        case (key, value) if key.endsWith("_tstamp") =>
          value.asString match {
            case Some(tstamp) =>
              key -> Json.fromString(tstamp.replace('T', ' ').takeWhile(c => c != '.' && c != 'Z'))
            case None =>
              key -> value
          }
        case (key, value) => (key, value)
      }
      JsonObject.fromMap(fields)
    }.noSpaces

  def insert[F[_]: Sync](env: Environment[F])(event: Event): F[Unit] = {
    val req = insertRequest(env).withBodyStream(Stream.emit(transform(event)).through(utf8Encode).covary[F])

    env.httpClient.run(req).use(_ => Sync[F].unit)
  }

  def insertRequest[F[_]](env: Environment[F]): Request[F] = {
    val uri = Uri
      .unsafeFromString(s"http://${env.config.output.good.host}:${env.config.output.good.port}")
      .withQueryParam("query", s"INSERT INTO ${env.config.output.good.database}.events FORMAT JSONEachRow")

    Request()
      .withMethod(Method.POST)
      .withUri(uri)
  }

  val BatchSize = 1000

  def mutateTable[F[_]: Sync](state: Ref[F, TableState], chunkTypes: Set[SchemaKey]): F[Unit] = {
    for {
      existingTypes <- state.get.map(_.keys)
      typesToAdd     = chunkTypes.map(_.toSchemaUri) -- existingTypes
      _             <- Sync[F].delay(println(s"Adding columns: ${typesToAdd.mkString(",")}"))
      _             <- state.update(m => m ++ typesToAdd.toList.map(x => x -> Definition.ColumnType.String).toMap)
    } yield ()
  }

  def insertion[F[_]: Timer: Concurrent](env: Environment[F]): Pipe[F, Event, Unit] = {
    in => in.groupWithin(BatchSize, 10.seconds).evalMap { chunk =>
      val chunkTypes = chunk.flatMap(e => Chunk(e.inventory.map(_.schemaKey).toSeq: _*)).toArray.toSet

      val req = insertRequest(env).withBodyStream(Stream.chunk(chunk).map(transform).intersperse("\n").through(utf8Encode))
      mutateTable[F](env.state, chunkTypes) *> env.httpClient.run(req).use(passOrFail[F])
    }
  }

  def decode[F[_]: Sync](res: Response[F]): F[String] =
    res.bodyText.compile.string.flatMap { s =>
      if (res.status.isSuccess) Sync[F].pure(s)
      else Sync[F].raiseError(new RuntimeException(s))
    }

  def passOrFail[F[_]: Sync](res: Response[F]): F[Unit] =
    if (res.status.isSuccess) Sync[F].unit
    else res.bodyText.compile.string.flatMap { s =>
      Sync[F].raiseError(new RuntimeException(s))
    }

  def build[F[_]](output: LoaderConfig.Good)(statement: String): Request[F] = {
    val uri = Uri.unsafeFromString(s"http://${output.host}:${output.port}")
    Request()
      .withMethod(Method.POST)
      .withUri(uri)
      .withBodyStream(Stream.emit(statement).through(utf8Encode).covary[F])
  }
}
