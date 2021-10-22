package com.snowplowanalytics.snowplow.clickhouse.loader

object Definition {

  /**
   * ClickHouse-specific data types
   */
  sealed trait ColumnType extends Product with Serializable {
    def toDdl: scala.Predef.String = this match {
      // CH types are case-sensitive
      case ColumnType.String          => "String"
      case ColumnType.Char(n)         => s"FixedString($n)"
      case ColumnType.Uuid            => "UUID"
      case ColumnType.Timestamp       => "DateTime"
      case ColumnType.Integer         => "Int32"
      case ColumnType.SmallInt        => "Int8"
      case ColumnType.Number          => "Float32"
      case ColumnType.Boolean         => "Boolean"
    }
  }
  object ColumnType {
    final case object String extends ColumnType
    final case class Char(size: Int) extends ColumnType
    final case object Uuid extends ColumnType
    final case object Timestamp extends ColumnType
    final case object Integer extends ColumnType
    final case object SmallInt extends ColumnType
    final case object Number extends ColumnType
    final case object Boolean extends ColumnType
    
    def parse(s: String): Either[String, ColumnType] = {
      val unparametrized = List(String, Uuid, Timestamp, Integer, SmallInt, Number, Boolean).collectFirst {
        case t if t.toDdl == s => t
      }

      unparametrized match {
        case Some(t) => Right(t)
        case None =>
          val Nbr = "(\\d+)".r
          s match {
            case s"FixedString(${Nbr(n1)})" => Right(Char(n1.toInt))
            case _ => Left(s)
          }
      }
    }
  }

  import ColumnType._

  final case class Column(name: scala.Predef.String, columnType: ColumnType, notNull: scala.Boolean = false) {
    def toDdl: String =
      s"$name ${columnType.toDdl}" ++ (if (notNull) " NOT NULL" else "")
  }

  val columns = List(
    // App
    Column("app_id",                  String),
    Column("platform",                String),

    // Data/time
    Column("etl_tstamp",              Timestamp),
    Column("collector_tstamp",        Timestamp, notNull = true),
    Column("dvce_created_tstamp",     Timestamp),

    // Event
    Column("event",                   String),
    Column("event_id",                Uuid, notNull = true),
    Column("txn_id",                  Integer),

    // Namespacing and versioning
    Column("name_tracker",            String),
    Column("v_tracker",               String),
    Column("v_collector",             String, notNull = true),
    Column("v_etl",                   String, notNull = true),

    // User id and visit
    Column("user_id",                 String),
    Column("user_ipaddress",          String),
    Column("user_fingerprint",        String),
    Column("domain_userid",           String),
    Column("domain_sessionidx",       SmallInt),
    Column("network_userid",          String),

    // Location
    Column("geo_country",             Char(2)),
    Column("geo_region",              Char(3)),
    Column("geo_city",                String),
    Column("geo_zipcode",             String),
    Column("geo_latitude",            Number),
    Column("geo_longitude",           Number),
    Column("geo_region_name",         String),

    // Ip lookups
    Column("ip_isp",                  String),
    Column("ip_organization",         String),
    Column("ip_domain",               String),
    Column("ip_netspeed",             String),

    // Page
    Column("page_url",                String),
    Column("page_title",              String),
    Column("page_referrer",           String),

    // Page URL components
    Column("page_urlscheme",          String),
    Column("page_urlhost",            String),
    Column("page_urlport",            Integer),
    Column("page_urlpath",            String),
    Column("page_urlquery",           String),
    Column("page_urlfragment",        String),

    // Referrer URL components
    Column("refr_urlscheme",          String),
    Column("refr_urlhost",            String),
    Column("refr_urlport",            Integer),
    Column("refr_urlpath",            String),
    Column("refr_urlquery",           String),
    Column("refr_urlfragment",        String),

    // Referrer details
    Column("refr_medium",             String),
    Column("refr_source",             String),
    Column("refr_term",               String),

    // Marketing
    Column("mkt_medium",              String),
    Column("mkt_source",              String),
    Column("mkt_term",                String),
    Column("mkt_content",             String),
    Column("mkt_campaign",            String),

    // Custom structured event
    Column("se_category",             String),
    Column("se_action",               String),
    Column("se_label",                String),
    Column("se_property",             String),
    Column("se_value",                Number),

    // Ecommerce
    Column("tr_orderid",              String),
    Column("tr_affiliation",          String),
    Column("tr_total",                Number),
    Column("tr_tax",                  Number),
    Column("tr_shipping",             Number),
    Column("tr_city",                 String),
    Column("tr_state",                String),
    Column("tr_country",              String),
    Column("ti_orderid",              String),
    Column("ti_sku",                  String),
    Column("ti_name",                 String),
    Column("ti_category",             String),
    Column("ti_price",                Number),
    Column("ti_quantity",             Integer),

    // Page ping
    Column("pp_xoffset_min",          Integer),
    Column("pp_xoffset_max",          Integer),
    Column("pp_yoffset_min",          Integer),
    Column("pp_yoffset_max",          Integer),

    // Useragent
    Column("useragent",               String),

    // Browser
    Column("br_name",                 String),
    Column("br_family",               String),
    Column("br_version",              String),
    Column("br_type",                 String),
    Column("br_renderengine",         String),
    Column("br_lang",                 String),
    Column("br_features_pdf",         Boolean),
    Column("br_features_flash",       Boolean),
    Column("br_features_java",        Boolean),
    Column("br_features_director",    Boolean),
    Column("br_features_quicktime",   Boolean),
    Column("br_features_realplayer",  Boolean),
    Column("br_features_windowsmedia", Boolean),
    Column("br_features_gears",       Boolean),
    Column("br_features_silverlight", Boolean),
    Column("br_cookies",              Boolean),
    Column("br_colordepth",           String),
    Column("br_viewwidth",            Integer),
    Column("br_viewheight",           Integer),

    // Operating System
    Column("os_name",                 String),
    Column("os_family",               String),
    Column("os_manufacturer",         String),
    Column("os_timezone",             String),

    // Device/Hardware
    Column("dvce_type",               String),
    Column("dvce_ismobile",           Boolean),
    Column("dvce_screenwidth",        Integer),
    Column("dvce_screenheight",       Integer),

    // Document
    Column("doc_charset",             String),
    Column("doc_width",               Integer),
    Column("doc_height",              Integer),

    // Currency
    Column("tr_currency",             Char(3)),
    Column("tr_total_base",           Number),
    Column("tr_tax_base",             Number),
    Column("tr_shipping_base",        Number),
    Column("ti_currency",             Char(3)),
    Column("ti_price_base",           Number),
    Column("base_currency",           Char(3)),

    // Geolocation
    Column("geo_timezone",            String),

    // Click ID
    Column("mkt_clickid",             String),
    Column("mkt_network",             String),

    // ETL Tags
    Column("etl_tags",                String),

    // Time event was sent
    Column("dvce_sent_tstamp",        Timestamp),

    // Referer
    Column("refr_domain_userid",      String),
    Column("refr_dvce_tstamp",        Timestamp),

    // Session ID
    Column("domain_sessionid",        Char(128)),

    // Derived timestamp
    Column("derived_tstamp",          Timestamp),

    // Event schema
    Column("event_vendor",            String),
    Column("event_name",              String),
    Column("event_format",            String),
    Column("event_version",           String),

    // Event fingerprint
    Column("event_fingerprint",       String),

    // True timestamp
    Column("true_tstamp",             Timestamp)
  )

  def create(db: scala.Predef.String): scala.Predef.String = {
    s"CREATE TABLE IF NOT EXISTS $db.events (${columns.map(_.toDdl).mkString(", ")}) ENGINE = MergeTree() ORDER BY collector_tstamp"
  }
}
