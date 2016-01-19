package com.leeriggins.slacklambda.rss

import org.joda.time._

case class RssFeed(
  rss: Rss)

case class Rss(
  version: Option[String],
  channel: Channel)

case class Channel(
  title: String,
  link: String,
  description: String,
  item: Seq[FeedItem])

case class FeedItem(
  title: String,
  link: String,
  href: Option[String],
  description: String,
  pubDate: DateTime,
  guid: String,
  channelId: Option[String])

