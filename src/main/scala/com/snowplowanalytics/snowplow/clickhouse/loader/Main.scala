/*
 * Copyright (c) 2020-2021 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.clickhouse.loader

import cats.effect.{IOApp, Sync, IO, ExitCode, ContextShift, Timer}
import cats.implicits._

import fs2.{ Stream, Pipe }
import fs2.io.file.{ directoryStream, readAll }
import fs2.text.{ utf8Decode, lines }

import org.log4s.getLogger

import com.snowplowanalytics.snowplow.badrows.Processor

import com.snowplowanalytics.snowplow.clickhouse.generated.BuildInfo
import com.snowplowanalytics.snowplow.analytics.scalasdk.Event
import cats.effect.Concurrent


object Main extends IOApp {

  lazy val logger = getLogger

  val processor = Processor(BuildInfo.name, BuildInfo.version)

  def run(args: List[String]): IO[ExitCode] =
    Cli.parse[IO](args).value.flatMap {
      case Right(cli) => 
        Environment.build[IO](cli).use { env =>
          val createTable: IO[Unit] = Loader.execute(env.httpClient, env.config.output.good)(Definition.create(env.config.output.good.database)) *> IO(println("events table has been created"))
          val appStream = Stream.eval(createTable).flatMap { _ =>
            eventStream[IO](env).through(load[IO](env))
          }

          appStream.compile.drain
        }.as(ExitCode.Success)
      case Left(error) => IO(System.err.println(error.show)).as(ExitCode.Error)
    }

  def eventStream[F[_]: Sync: ContextShift](env: Environment[F]): Stream[F, Event] =
    directoryStream[F](env.blocker, env.config.input.path).flatMap { file =>
      readAll[F](file, env.blocker, 4096)
        .through(utf8Decode[F])
        .through(lines[F])
        .map(line => Event.parse(line).toEither)
        .evalMap {
          case Right(event) => Sync[F].pure(event)
          case Left(badRow) => Sync[F].raiseError[Event](new IllegalArgumentException(badRow.toString))
        }
    }

  def load[F[_]: Timer: Concurrent](env: Environment[F]): Pipe[F, Event, Unit] = {
    in => in.through(Loader.insertion[F](env)).void
  }
}
