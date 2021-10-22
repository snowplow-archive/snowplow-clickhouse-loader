package com.snowplowanalytics.snowplow.clickhouse.loader

import cats.implicits._

import com.snowplowanalytics.snowplow.clickhouse.loader.Definition.{ ColumnType }

object Transformation {
  type TableState = Map[String, ColumnType]

  def parse(description: String): Either[String, TableState] = {
    val types = description.split("\n").toList.traverse { line =>
      line.split("\t").toList match {
        case List(name, t) => Right((name, t))
        case _ => Left(s"Wrong columns in ${line}")
      }
    }

    types.flatMap { parsed =>
      val state = parsed.traverse {
        case (n, t) => Definition.ColumnType.parse(t).map { tt => (n, tt) }
      }

      state.map(_.toMap)
    }
  }
}
