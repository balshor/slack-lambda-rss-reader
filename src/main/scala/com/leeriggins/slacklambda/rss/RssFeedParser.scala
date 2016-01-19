package com.leeriggins.slacklambda.rss

import java.text.SimpleDateFormat

import org.json4s._
import org.json4s.Extraction._
import org.json4s.Xml._
import org.json4s.ext._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.{ read, write }

import org.joda.time._
import org.joda.time.format.DateTimeFormat

object RssFeedParser {

  implicit val formats = {
    val rfc822SimpleDateFormat = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z")
    val rfc822DateTimeFormat = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss z")

    case object Rfc822DateFormat extends CustomSerializer[DateTime]({ formats =>
      ({
        case JString(str) => {
          new DateTime(rfc822SimpleDateFormat.parse(str))
        }
        case JNull => null
      }, {
        case dateTime: DateTime => {
          JString(rfc822DateTimeFormat.print(dateTime))
        }
      })
    })
    
    DefaultFormats + Rfc822DateFormat
  }
  
  def parse(xmlStr: String): RssFeed = {
    val xml = scala.xml.XML.loadString(xmlStr)
    toJson(xml).extract[RssFeed]
  }

}
