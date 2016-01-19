package com.leeriggins.slacklambda.rss

import org.apache.http.client._
import org.apache.http.client.methods.HttpGet
import scala.util._
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

class UrlFeedSource(url: String, client: HttpClient) extends (() => Try[RssFeed]) {

  override def apply(): Try[RssFeed] = {
    val get = new HttpGet(url)
    val responseAttempt = Try { client.execute(get) }

    responseAttempt.flatMap { response =>
      if (response.getStatusLine.getStatusCode != 200) {
        Failure(new HttpResponseException(response.getStatusLine.getStatusCode, response.getStatusLine.getReasonPhrase))
      } else {
        Try {
          val entity = response.getEntity
          val contents = io.Source.fromInputStream(response.getEntity.getContent, "UTF-8").mkString
          EntityUtils.consumeQuietly(entity)
          RssFeedParser.parse(contents)
        }
      }
    }
  }

}

object UrlFeedSource extends App {

  val url = "http://flowingdata.com/feed/"

  val client = HttpClients.createMinimal()
  try {
    val source = new UrlFeedSource(url, client)

    val feed = source.apply()

    feed.get.rss.channel.item.foreach { item =>
      println(SlackRenderer(item))
      println()
    }
  } finally {
    client.close()
  }
}
