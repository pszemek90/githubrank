package io.scalac.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.{JsonFraming, Keep, Sink}
import io.scalac.JsonProtocol
import io.scalac.Model.{Contributor, Repo}
import spray.json._

import scala.language.postfixOps

object GitHubActor {
	case class FetchContributors(organization: String)
	case class FetchRepoPages(organization: String)
	case class RepoPagesFetched(numberOfPages: Int)
	case class FetchRepos(organization: String, numberOfPages: Int)
	case class ReposFetched(repos: Seq[Repo])
	case class FetchContributorsForRepo(repo: Repo, organization: String)
	case class ContributorsFetched(contributors: Seq[Contributor])
}

class GitHubActor extends Actor with JsonProtocol with ActorLogging {
	
	import GitHubActor._
	import io.scalac.Global._
	
	var requestSent = 0
	
	override def receive: Receive = {
		case FetchContributors(organization) =>
			val pagesFetcherActor = context.actorOf(Props[RepoPagesFetcherActor], "pagesFetcherActor")
			pagesFetcherActor ! FetchRepoPages(organization)
			context.become(collectInfoForOrganization(organization, sender()))
	}
	
	def collectInfoForOrganization(organization: String, originalSender: ActorRef): Receive = {
		case RepoPagesFetched(numberOfPages) =>
			log.info(s"Number of pages to go through: $numberOfPages")
			val repoFetcherActor = context.actorOf(Props[RepoFetcherActor], "repoFetcherActor")
			repoFetcherActor ! FetchRepos(organization, numberOfPages)
		case ContributorsFetched(contributors) =>
			originalSender ! contributors
			context.unbecome()
		case message => log.info(s"Type of message received: $message")
	}
	
	def runRequest[T](req: HttpRequest)(implicit reader: JsonReader[T]) = {
		requestSent += 1
		log.info(s"Fetching request no $requestSent: ${req.uri}")
		val eventualResponse = Http().singleRequest(req)
		eventualResponse.flatMap(
			_.entity
				.dataBytes
				.via(JsonFraming.objectScanner(Int.MaxValue))
				.map(_.utf8String)
				.map(_.parseJson.convertTo[T])
				.toMat(Sink.seq)(Keep.right)
				.run()
		)
	}
}