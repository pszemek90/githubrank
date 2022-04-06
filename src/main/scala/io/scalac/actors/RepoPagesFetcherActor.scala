package io.scalac.actors

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Link, LinkParams, RawHeader}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.pattern.pipe

class RepoPagesFetcherActor extends Actor with ActorLogging {
	
	import GitHubActor._
	import io.scalac.Global._
	
	override def receive: Receive = {
		case FetchRepoPages(organization) =>
			checkNumberOfPages(organization)
			context.stop(self)
	}
	
	def checkNumberOfPages(organization: String) = {
		log.info(s"Checking number of pages for $organization")
		val responseFuture = Http().singleRequest(HttpRequest(uri = s"https://api.github.com/orgs/$organization/repos",
			headers = List(RawHeader(authorizationName, envToken))))
		
		responseFuture.map {
			case HttpResponse(StatusCodes.OK, headers, entity, _) =>
				val linkHeader = headers.collect {
					case Link(x) => x
				}
				entity.discardBytes()
				val pages = linkHeader.flatten
					.filter(_.params.contains(LinkParams.last))
					.map(_.uri)
					.map(_.query().value)
					.toList.headOption.getOrElse("1").toInt
				RepoPagesFetched(pages)
		}.pipeTo(sender())
	}
	
}
