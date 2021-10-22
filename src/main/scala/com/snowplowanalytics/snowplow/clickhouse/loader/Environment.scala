package com.snowplowanalytics.snowplow.clickhouse.loader

import cats.implicits._

import io.circe.Json

import cats.effect.{ ConcurrentEffect, Blocker, Resource }
import cats.effect.concurrent.Ref

import org.http4s.client.{ Client => HttpClient }
import org.http4s.client.blaze.BlazeClientBuilder

import com.snowplowanalytics.iglu.client.{ Client => IgluClient }
import com.snowplowanalytics.snowplow.clickhouse.loader.Transformation.TableState

final case class Environment[F[_]](blocker: Blocker, httpClient: HttpClient[F], state: Ref[F, TableState], igluClient: IgluClient[F, Json], config: LoaderConfig)

object Environment {

  def build[F[_]: ConcurrentEffect](cli: Cli[F]): Resource[F, Environment[F]] = {
    for {
      blocker    <- Blocker[F]
      httpClient <- BlazeClientBuilder[F](scala.concurrent.ExecutionContext.global).resource
      parsedState = Loader.execute[F](httpClient, cli.config.output.good)(s"DESCRIBE TABLE ${cli.config.output.good.database}.events").map(s => Transformation.parse(s))
      recovered   = parsedState.recoverWith {
        case _ => ConcurrentEffect[F].pure(Right(Map.empty))
      }
      state      <- Resource.eval(recovered.flatMap(p => ConcurrentEffect[F].fromEither(p.leftMap(e => new RuntimeException(e)))).flatMap(s => Ref.of(s)))

    } yield Environment[F](blocker, httpClient, state, cli.iglu, cli.config)
  }
}
