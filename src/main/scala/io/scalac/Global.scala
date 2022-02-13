package io.scalac

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import scala.concurrent.duration._

object Global {
	implicit val system = ActorSystem()
	implicit val materializer = ActorMaterializer()
	implicit val timeout = Timeout(5 minutes)
}
