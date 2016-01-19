package com.leeriggins.slacklambda.chat

import com.leeriggins.slacklambda.rss._
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import org.apache.http.impl.client.HttpClients
import scala.util.{ Failure, Success }

object ChatProcessor {
  case class FeedInfo(name: String, url: String, description: String)
  val feeds = IndexedSeq(
    FeedInfo("xkcd", "https://www.xkcd.com/rss.xml", "A Webcomic of Romance, Sarcasm, Math, and Language."),
    FeedInfo("nytimes", "http://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "New York Times Homepage"),
    FeedInfo("hn", "http://hnrss.org/newest?points=300", "Hacker News 300+ Point Articles"),
    FeedInfo("reddit", "https://www.reddit.com/.rss", "Reddit Front Page"),
    FeedInfo("flowingdata", "http://flowingdata.com/feed/", "Flowing Data"))

  val savedFeedName = "saved"

  val maxCountPerRequest = 5

  val help = """help"""r
  val listFeeds = """list\s+feeds"""r
  val retrieve = """retrieve\s+(.*)"""r
  val read = """read\s+(.*)"""r
  val last = """last"""r
  val more = """more"""r
  val save = """save\s+(\d+)"""r

  val helpText = """*Help*
                   |Here are the commands that I know:
                   |  * help - display this text
                   |  * list feeds - display the list of feeds that I know
                   |  * retrieve [feed] - retrieve the given feed from the source
                   |  * read [feed] - read the specified feed from the start
                   |  * last - show the previously shown 5 items
                   |  * more - display more entries from the current feed
                   |  * save [item number] - save the specified item from the current feed
""".stripMargin
}

case class UserContext(
  `team_id`: String,
  `user_id`: String,
  currentFeed: Option[String] = None,
  currentItems: List[FeedItem] = List())

class ChatProcessor(implicit val dynamoDb: DynamoDB) extends ((UserContext, String) => (UserContext, String)) {
  import ChatProcessor._

  private lazy val client = HttpClients.createMinimal()

  /* Given the current context and chat message, return the new context and response. */
  override def apply(context: UserContext, chatText: String): (UserContext, String) = {
    chatText.trim.toLowerCase match {
      case help() => {
        (context, helpText)
      }
      case listFeeds() => {
        val prefix = "*Feed List*\nHere are the feeds that I know:\n"
        val feedStrs = feeds.map { feedInfo =>
          s"  * ${feedInfo.name} - ${feedInfo.description}"
        }
        val savedFeedStr = s"  * saved - your saved items"

        (context, (prefix +: feedStrs :+ savedFeedStr).mkString("\n"))
      }
      case retrieve(channel) => {
        feeds.find(_.name == channel).map { info =>
          val retrieveAttempt = new UrlFeedSource(info.url, client).apply().map { rssFeed =>
            rssFeed.rss.channel.item.toIndexedSeq
          }

          retrieveAttempt match {
            case Success(items) => {
              Repository.forFeed(channel).putAll(items)
              (context, s"'${channel}' reloaded.  You probably want to 'read ${channel}' next.")
            }
            case Failure(exception) => {
              exception.printStackTrace()
              (context, s"${exception.getClass.getSimpleName} reloading ${channel}: ${exception.getMessage}")
            }
          }
        }.getOrElse {
          (context, s"I don't know the feed '${channel}'.\nYou can see the list of available feeds by using 'list feeds'.")
        }
      }
      case read(channel) => {
        if (channel != savedFeedName && !feeds.exists(_.name == channel)) {
          (context, s"I don't know the feed '${channel}'.\nYou can see the list of available feeds by using 'list feeds'.")
        } else {
          val repo = if (channel == savedFeedName) {
            Repository.forSavedItems(context.`user_id`)
          } else {
            Repository.forFeed(channel)
          }

          val items = repo.getAll(maxCount = Some(maxCountPerRequest))
          val prefix = s"Now reading ${channel}."
          val lines = SlackRenderer(items)

          (context.copy(currentFeed = Some(channel), currentItems = items.toList), (prefix +: lines).mkString("\n"))
        }
      }
      case last() => {
        (context.currentFeed, context.currentItems) match {
          case (None, _) => {
            (context, "No feed currently selected.\nYou can say 'help' if you want help.")
          }
          case (Some(channel), IndexedSeq()) => {
            (context, s"${channel}, no items to show.")
          }
          case (Some(channel), items) => {
            (context, (s"${channel}:" +: SlackRenderer(items)).mkString("\n"))
          }
        }
      }
      case more() => {
        context.currentFeed match {
          case None => {
            (context, "No feed currently selected.\nYou can say 'help' if you want help.")
          }
          case Some(channel) => {
            val repo = if (channel == savedFeedName) {
              Repository.forSavedItems(context.`user_id`)
            } else {
              Repository.forFeed(channel)
            }

            val items = repo.getAll(since = context.currentItems.lastOption, maxCount = Some(maxCountPerRequest))

            val prefix = s"More items from ${channel}:"
            val lines = SlackRenderer(items)

            (context.copy(currentItems = items.toList), (prefix +: lines).mkString("\n"))
          }
        }
      }
      case save(itemNumber) => {
        context.currentFeed match {
          case None => {
            (context, "No feed currently selected.\nYou can say 'help' if you want help.")
          }
          case Some(channel) if channel == savedFeedName => {
            (context, "You can't save items from your saved item feed.")
          }
          case Some(channel) => {
            val index = itemNumber.toInt - 1
            if (context.currentItems.isDefinedAt(index)) {
              val itemToSave = context.currentItems(index).copy(channelId = Some(channel))
              Repository.forSavedItems(context.`user_id`).put(itemToSave)
              (context, s"Saved ${SlackRenderer(itemToSave)}.")
            } else {
              (context, s"There is no item ${itemNumber} to save.")
            }
          }
        }

      }
      case _ => {
        (context, s"I'm sorry, but I don't know what '${chatText}' means.\nYou can say 'help' if you want help.")
      }
    }
  }
}
