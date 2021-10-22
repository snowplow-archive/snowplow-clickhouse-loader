[![License][license-image]][license]
[![Coverage Status][coveralls-image]][coveralls]
[![Test][test-image]][test]
[![Docker][docker-image]][docker]

# Snowplow ClickHouse Loader

## Quickstart

Assuming [Docker][docker] is installed:

1. Run the ClickHouse server

```
$ docker run -d -p 8123:8123 \
    --name some-clickhouse-server \
    --ulimit nofile=262144:262144 \
    --volume=$HOME/clickhouse_db_vol:/var/lib/clickhouse yandex/clickhouse-server
```

2. Start the client shell:

```
$ docker run -it \
    --rm \
    --link some-clickhouse-server:clickhouse-server yandex/clickhouse-client \
    --host clickhouse-server
```

3. Make sure your database is created (`tutorial` in this example).
   You can keep working in this session to check the loaded data:

```
:) CREATE DATBASE IF NOT EXISTS tutorial
:) USE tutorial
```

4. Run the Loader (config implies `tutoral` DB and some enriched data on local FS):

```
$ sbt
> run --config config/config.local.minimal.hocon --resolver config/resolver.json
```

It will automatically load the data from your local filesystem in batches of 1000 (configurable in the code)

## Copyright and License

Snowplow ClickHouse Loader is copyright 2021 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0][license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[config]: ./config/
[resolver]: ./config/resolver.json

[docker]: https://www.docker.com/
[iglu-server]: https://github.com/snowplow-incubator/iglu-server

[docker]: https://hub.docker.com/r/snowplow/snowplow-clickhouse-loader/tags
[docker-image]: https://img.shields.io/docker/v/snowplow/snowplow-clickhouse-loader/latest

[test]: https://github.com/snowplow-incubator/snowplow-clickhouse-loader/actions?query=workflow%3ATest
[test-image]: https://github.com/snowplow-incubator/snowplow-clickhouse-loader/workflows/Test/badge.svg

[license]: http://www.apache.org/licenses/LICENSE-2.0
[license-image]: http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat

[coveralls]: https://coveralls.io/github/snowplow-incubator/snowplow-clickhouse-loader?branch=master
[coveralls-image]: https://coveralls.io/repos/github/snowplow-incubator/snowplow-clickhouse-loader/badge.svg?branch=master
