package com.leeriggins.slacklambda.rss

/**
 * Renders feed items and saved feed items into text suitable for sending to Slack for display.
 *
 * This is best (ie, minimal) effort only, meaning that we don't do anything to avoid accidental markdown in the original feed.
 */
object SlackRenderer {
  def apply(items: Seq[FeedItem]): Seq[String] = {
    if (items.isEmpty) {
      IndexedSeq("  (No more items.)")
    } else {
      items.zipWithIndex.map { case (item, index) =>
        s"  ${index+1}. ${SlackRenderer(item)}"
      }
    }
  }

  def apply(item: FeedItem): String = {
    val prefix = item.channelId.map(escape(_) + ": ").getOrElse("")

    val text = escape(item.title)
    val suffix = (item.href orElse Option(item.link)).map { link =>
      s"<${link}|${text}>"
    }.getOrElse(text)

    prefix + suffix
  }

  // basic escape as per https://api.slack.com/docs/formatting 
  private def escape(str: String): String = {
    str.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
  }
}
