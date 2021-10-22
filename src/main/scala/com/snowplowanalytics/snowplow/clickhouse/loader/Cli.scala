package com.snowplowanalytics.snowplow.clickhouse.loader

import java.nio.file.Path

import io.circe.Json
import io.circe.parser.{ parse => parseJson }

import cats.effect.{Async}
import cats.data.EitherT

import cats.implicits._

import com.monovore.decline.{ Opts, Command }


import com.snowplowanalytics.iglu.client.Client

import com.snowplowanalytics.snowplow.clickhouse.generated.BuildInfo
import cats.effect.Sync


case class Cli[F[_]](config: LoaderConfig, iglu: Client[F, Json])

object Cli {
  /** Parse list of arguments, validate against schema and initialize */
  def parse[F[_]: Async](args: List[String]): EitherT[F, String, Cli[F]] =
    command.parse(args) match {
      case Left(help)       => EitherT.leftT[F, Cli[F]](help.show)
      case Right(rawConfig) => fromRawConfig[F](rawConfig)
    }

  def fromRawConfig[F[_]: Async](rawConfig: RawConfig): EitherT[F, String, Cli[F]] =
    for {
      configContent <- EitherT.liftF(Sync[F].delay(scala.io.Source.fromFile(rawConfig.config.toFile).mkString))
      config <- EitherT.fromEither[F](parseJson(configContent).flatMap(_.as[LoaderConfig])).leftMap(_.show)
      resolverContent <- EitherT.liftF(Sync[F].delay(scala.io.Source.fromFile(rawConfig.resolver.toFile).mkString))
      resolverJson <- EitherT.fromEither[F](parseJson(resolverContent)).leftMap(_.show)
      client <- Client.parseDefault[F](resolverJson).leftMap(_.show)
    } yield Cli(config, client)

  val resolver = Opts.option[Path](
    long = "resolver",
    help = "Iglu Resolver JSON config, FS path or base64-encoded"
  )

  val config = Opts.option[Path](
    long = "config",
    help = "Self-describing JSON configuration"
  )

  final case class RawConfig(config: Path, resolver: Path)

  private val command: Command[RawConfig] =
    Command[(Path, Path)](BuildInfo.name, BuildInfo.version)((config, resolver).tupled).mapValidated {
      case (cfg, res) => RawConfig(cfg, res).validNel
    }
}

