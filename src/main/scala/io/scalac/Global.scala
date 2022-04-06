package io.scalac

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.language.postfixOps

object Global {
	implicit val system = ActorSystem()
	implicit val timeout = Timeout(5 minutes)
	implicit val dispatcher = system.dispatcher
	implicit val authorizationName = "Authorization"
	val config = ConfigFactory.load()
	implicit val envToken = config.getString("github.token")
	
	
}
