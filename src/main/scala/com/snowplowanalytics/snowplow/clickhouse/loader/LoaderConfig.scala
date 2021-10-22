package com.snowplowanalytics.snowplow.clickhouse.loader

import java.nio.file.Path

import cats.implicits._

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import com.snowplowanalytics.snowplow.clickhouse.loader.LoaderConfig._
import java.nio.file.InvalidPathException

case class LoaderConfig(input: Source, output: Sink)

object LoaderConfig {
  final case class Source(path: Path)

  final case class Good(host: String, port: Int, database: String)

  final case class Sink(good: Good)

  implicit val ioCircePathDecoder: Decoder[Path] =
    Decoder[String].emap { path =>
      Either.catchOnly[InvalidPathException](Path.of(path)).leftMap(e => s"Cannot parse string $path into Path. $e")
    }
  implicit val ioCircePathEncoder: Encoder[Path] =
    Encoder[String].contramap(_.toString)

  implicit val ioCirceGoodDecoder: Decoder[Good] =
    deriveDecoder[Good]
  implicit val ioCirceGoodEncoder: Encoder[Good] =
    deriveEncoder[Good]

  implicit val ioCirceSourceDecoder: Decoder[Source] =
    deriveDecoder[Source]
  implicit val ioCirceSourceEncoder: Encoder[Source] =
    deriveEncoder[Source]

  implicit val ioCirceSinkDecoder: Decoder[Sink] =
    deriveDecoder[Sink]
  implicit val ioCirceSinkEncoder: Encoder[Sink] =
    deriveEncoder[Sink]

  implicit val ioCirceLoaderConfigDecoder: Decoder[LoaderConfig] =
    deriveDecoder[LoaderConfig]
  implicit val ioCirceLoaderConfigEncoder: Encoder[LoaderConfig] =
    deriveEncoder[LoaderConfig]
}

