package com.leeriggins.slacklambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.kms.AWSKMSClient
import com.amazonaws.services.kms.model.DecryptRequest
import java.nio.ByteBuffer
import org.apache.http.client.HttpResponseException
import java.util.Base64
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import org.apache.http.impl.client.HttpClients
import com.leeriggins.slacklambda.rss.UrlFeedSource
import com.leeriggins.slacklambda.rss.SlackRenderer
import scala.util.{ Try, Success, Failure }
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.leeriggins.slacklambda.chat.ChatProcessor
import com.leeriggins.slacklambda.rss.Repository
import com.leeriggins.slacklambda.chat.UserContext

trait SlackWebhookHandler {
  import SlackWebhookHandler._

  private lazy val tokens: Set[String] = {
    val kms = new AWSKMSClient()
    encryptedTokens.map { encryptedToken =>
      val encryptedBytes = ByteBuffer.wrap(Base64.getDecoder.decode(encryptedToken))
      val decryptedKeyBytes = kms.decrypt(new DecryptRequest().withCiphertextBlob(encryptedBytes)).getPlaintext
      new String(decryptedKeyBytes.array())
    }
  }

  def handler(request: WebhookRequest, context: Context): WebhookResponse = {

    if (!tokens.contains(request.token)) {
      throw new HttpResponseException(401, "401 Invalid token.")
    } else {
      internalHandler(request, context)
    }
  }

  protected def internalHandler(request: WebhookRequest, context: Context): WebhookResponse

}

object SlackWebhookHandler {
  // encrypted Slack webhook token
  private val encryptedTokens: Set[String] = Set(
    "CiAWco9FsDGEw3NDrWCCnYLFwtSWo9BefHmrhCl3HY96SRKfAQEBAgB4FnKPRbAxhMNzQ61ggp2CxcLUlqPQXnx5q4Qpdx2PekkAAAB2MHQGCSqGSIb3DQEHBqBnMGUCAQAwYAYJKoZIhvcNAQcBMB4GCWCGSAFlAwQBLjARBAzTd+C2j0Da4tDTBPYCARCAM1f/S0l4fDmrpJ/maZh5caHr6/qVzcO69MZ/Bxml/d7/BDp71XDypfBF70zXuxVrS5V36w==",
    "CiAWco9FsDGEw3NDrWCCnYLFwtSWo9BefHmrhCl3HY96SRKfAQEBAgB4FnKPRbAxhMNzQ61ggp2CxcLUlqPQXnx5q4Qpdx2PekkAAAB2MHQGCSqGSIb3DQEHBqBnMGUCAQAwYAYJKoZIhvcNAQcBMB4GCWCGSAFlAwQBLjARBAyopN7gc5MCsrmngRsCARCAMyrKsKYG6ca5vqOQvaxJZyVZXfkx3CM2k3AYf3R9AEj6QZ5M5jUCVctIhnDj+xx0dQifQg==")
}

/** Echos the request (minus the token) back to Slack. */
class EchoHandler extends SlackWebhookHandler {
  override protected def internalHandler(request: WebhookRequest, context: Context): WebhookResponse = {
    if (Option(request.text).exists(_.toLowerCase.contains("respond"))) {
      WebhookResponse(request.copy(token = null).toString)
    } else {
      WebhookResponse(null)
    }
  }
}

class RssFeedHandler extends SlackWebhookHandler {

  private implicit lazy val dyamoDb: DynamoDB = new DynamoDB(new AmazonDynamoDBClient())
  private lazy val processor = new ChatProcessor()

  override protected def internalHandler(request: WebhookRequest, context: Context): WebhookResponse = {
    val userRepo = Repository.forUserContext(request.team_id, request.user_id)
    val userContext = {
      val items = userRepo.getAll(maxCount = Some(1))
      items.headOption.getOrElse(UserContext(request.team_id, request.user_id))
    }

    val decodedText = java.net.URLDecoder.decode(request.text, "UTF-8")
    val (newContext, response) = processor.apply(userContext, decodedText)

    userRepo.put(newContext)

    WebhookResponse(text = response)
  }
}

