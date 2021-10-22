package com.snowplowanalytics.snowplow.clickhouse.loader


case class Cli[F[_]](config: LoaderConfig, iglu: Client[F, Json])

