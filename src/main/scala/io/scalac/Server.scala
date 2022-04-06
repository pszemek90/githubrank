package io.scalac

import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import io.scalac.actors.GitHubActor
import akka.pattern.ask
import io.scalac.Model.Contributor

import scala.concurrent.Future

object Server extends App with JsonProtocol with LazyLogging {
	import io.scalac.Global._
	import io.scalac.actors.GitHubActor._
	import system.dispatcher
	
	val gitHubActor = system.actorOf(Props[GitHubActor], "gitHubActor")
	
	val route =
		(path("org" / Segment / "contributors") & get) { organization =>
			complete((gitHubActor ? FetchContributors(organization)).mapTo[Seq[Contributor]])
		}
	
	Http().bindAndHandle(route, "localhost", 8080)
}
