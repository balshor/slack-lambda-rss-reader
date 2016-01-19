package com.leeriggins.slacklambda

import scala.beans.BeanInfo
import com.amazonaws.services.lambda.runtime.Context
import scala.beans.BeanProperty

@BeanInfo
case class WebhookRequest(
  @BeanProperty var token: String,
  @BeanProperty var team_id: String = null,
  @BeanProperty var team_domain: String = null,
  @BeanProperty var channel_id: String = null,
  @BeanProperty var channel_name: String = null,
  @BeanProperty var timestamp: String = null,
  @BeanProperty var user_id: String = null,
  @BeanProperty var user_name: String = null,
  @BeanProperty var text: String = null,
  @BeanProperty var trigger_word: String = null
) {
  def this() = this(null)
}

@BeanInfo
case class WebhookResponse(
  @BeanProperty text: String,
  @BeanProperty username: String = null,
  @BeanProperty parse: String = null,
  @BeanProperty link_names: String = null,
  @BeanProperty status: Int = 200,
  @BeanProperty errorMessage: String = null
)
