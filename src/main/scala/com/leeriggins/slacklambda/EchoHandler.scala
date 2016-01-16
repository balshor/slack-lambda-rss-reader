package com.leeriggins.slacklambda

import com.amazonaws.services.lambda.runtime.Context

class EchoHandler {
  
  def handler(request: WebhookRequest, context: Context): WebhookResponse = {
    WebhookResponse("I got: " + request.toString)
  }
  
}