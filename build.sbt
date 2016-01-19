enablePlugins(AwsLambdaPlugin)

lazy val root = (project in file(".")).
  settings(
    organization := "com.leeriggins",
    name := "slack-lambda-rss-reader",
    version := "0.0.1",
    scalaVersion := "2.11.7"
  ).
  settings(
    libraryDependencies ++= dependencies
  )

lazy val dependencies = Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
  "com.amazonaws" % "aws-lambda-java-events" % "1.1.0",
  "com.amazonaws" % "aws-lambda-java-log4j" % "1.0.0",
  "org.json4s" %% "json4s-jackson" % "3.3.0",
  "org.json4s" %% "json4s-ext" % "3.3.0"
)

lambdaName := Some("slack-lambda-example")
handlerName := Some("com.leeriggins.slacklambda.RssFeedHandler::handler")
region := Some("us-east-1")
roleArn := Some("arn:aws:iam::640434276206:role/slack-lambda")
s3Bucket := Some("com.leeriggins.lambda")
