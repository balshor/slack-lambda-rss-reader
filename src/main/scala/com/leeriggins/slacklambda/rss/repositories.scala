package com.leeriggins.slacklambda.rss

import scala.collection.JavaConverters._
import org.json4s._
import org.json4s.jackson.Serialization._
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.model._
import org.joda.time.DateTime
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import org.apache.http.impl.client.HttpClients
import com.leeriggins.slacklambda.chat.UserContext

trait Repository[T <: AnyRef] {
  def putAll(items: Seq[T]): Unit
  def getAll(since: Option[T] = None, maxCount: Option[Int] = None): IndexedSeq[T]
  
  def put(items: T*): Unit = putAll(items.toIndexedSeq)
}

object Repository {
  
  def forFeed(feedName: String)(implicit dynamoDb: DynamoDB): Repository[FeedItem] = {
    new DynamoDbRepository[FeedItem](dynamoDb, RepositoryType.feed, feedName, DynamoDbTableConstants.tableName, _.pubDate.getMillis)
  }
  
  def forSavedItems(userId: String)(implicit dynamoDb: DynamoDB): Repository[FeedItem] = {
    new DynamoDbRepository[FeedItem](dynamoDb, RepositoryType.saved, userId, DynamoDbTableConstants.tableName, _.pubDate.getMillis)
  }
  
  def forUserContext(teamId: String, userId: String)(implicit dynamoDb: DynamoDB): Repository[UserContext] = {
    new DynamoDbRepository[UserContext](dynamoDb, RepositoryType.context, s"${teamId}:${userId}", DynamoDbTableConstants.tableName, (_ => 0L))
  }
  
}

object DynamoDbTableConstants {
  val tableName = "rssItems"
  val hashKeyName = "channelId"
  val rangeKeyName = "timestamp"
}

class DynamoDbRepository[T <: AnyRef : Manifest](
    dynamoDb: DynamoDB,
    repositoryType: RepositoryType,
    partitionId: String,
    tableName: String,
    getRangeKey: T => Long) extends Repository[T] {
  import DynamoDbTableConstants._

  implicit val formats = RssFeedParser.formats

  private val hashKeyValue = s"${repositoryType}:${partitionId}"

  override def putAll(items: Seq[T]): Unit = {
    val tableItems = items.map { item =>
      val rangeKeyValue = getRangeKey(item)
      new Item()
        .withPrimaryKey(hashKeyName, hashKeyValue, rangeKeyName, rangeKeyValue)
        .withJSON("document", write[T](item))
    }

    val tableWriteItems = new TableWriteItems(tableName).withItemsToPut(tableItems.asJava)

    val outcome = dynamoDb.batchWriteItem(tableWriteItems)
    Iterator.iterate(outcome.getUnprocessedItems) { unprocessedItems =>
      if (!unprocessedItems.isEmpty) {
        dynamoDb.batchWriteItemUnprocessed(unprocessedItems).getUnprocessedItems
      } else {
        unprocessedItems
      }
    }.takeWhile(!_.isEmpty).foreach { unprocessedItems =>
      println(s"${unprocessedItems} remaining.")
    }
  }

  override def getAll(since: Option[T] = None, maxCount: Option[Int] = None): IndexedSeq[T] = {
    val table = dynamoDb.getTable(tableName)
    val querySpec = {
      val withHashKey = new QuerySpec()
        .withHashKey(new KeyAttribute(hashKeyName, hashKeyValue))
        .withConsistentRead(true)
        .withScanIndexForward(false)

      val withSince = since.map { t =>
        withHashKey.withExclusiveStartKey(hashKeyName, hashKeyValue, rangeKeyName, getRangeKey(t))
      } getOrElse {
        withHashKey
      }
      
      val withMaxCount = maxCount.map { max =>
        withSince.withMaxResultSize(max)
      } getOrElse {
        withSince
      }
      
      withMaxCount
    }

    val outcomes = table.query(querySpec)
    outcomes.iterator().asScala.map { item =>
      val json = item.getJSON("document")
      read[T](json)
    }.toIndexedSeq
  }

}

object DynamoDbRepository {
  import DynamoDbTableConstants._

  def createTables(dynamoDb: DynamoDB): Unit = {
    val feedItemsKeySchema = IndexedSeq(
      new KeySchemaElement().withAttributeName(hashKeyName).withKeyType(KeyType.HASH),
      new KeySchemaElement().withAttributeName(rangeKeyName).withKeyType(KeyType.RANGE))

    val attributeDefinitions = IndexedSeq(
      new AttributeDefinition().withAttributeName(hashKeyName).withAttributeType(ScalarAttributeType.S),
      new AttributeDefinition().withAttributeName(rangeKeyName).withAttributeType(ScalarAttributeType.N))

    val provisionedThroughput = new ProvisionedThroughput()
      .withReadCapacityUnits(2L)
      .withWriteCapacityUnits(2L)

    val request = new CreateTableRequest()
      .withTableName(tableName)
      .withKeySchema(feedItemsKeySchema.asJava)
      .withAttributeDefinitions(attributeDefinitions.asJava)
      .withProvisionedThroughput(provisionedThroughput)

    dynamoDb.createTable(request)
  }

  def main(args: Array[String]): Unit = {
    implicit val dynamoDb = new DynamoDB(new AmazonDynamoDBClient)
    
    val repo = Repository.forFeed("flowingdata")

    val client = HttpClients.createMinimal()
    val rss = new UrlFeedSource("http://flowingdata.com/feed/", client).apply()
    repo.putAll(rss.get.rss.channel.item)

    val retrieved = repo.getAll()
    retrieved.toIndexedSeq.foreach(println)
  }

}

// enum used to ensure that multiple repository types can coexist in a single table via partitioning by key prefix
abstract sealed trait RepositoryType
object RepositoryType {
  case object feed extends RepositoryType
  case object saved extends RepositoryType
  case object context extends RepositoryType
}
