package io.scalac.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.headers._
import akka.stream.scaladsl.{JsonFraming, Keep, Sink}
import com.typesafe.config.ConfigFactory
import io.scalac.JsonProtocol
import io.scalac.Model.{Contributor, Repo}
import spray.json._

object GitHubActor {
	case class FetchContributors(organization: String)
}

class GitHubActor extends Actor with JsonProtocol with ActorLogging {
	
	import GitHubActor._
	import io.scalac.Global._
	import system.dispatcher
	
	override def receive: Receive = {
		case FetchContributors(organization) =>
			checkNumberOfPages(organization)
			context.become(collectInfoForOrganization(organization, sender()))
	}
	
	def collectInfoForOrganization(organization: String, originalSender: ActorRef): Receive = {
		case pagesNumber: Int =>
			log.info(s"Number of pages to go through: $pagesNumber")
			fetchRepos(organization, pagesNumber)
		case repos: Seq[Repo] =>
			log.info(s"Fetched repositories: $repos")
			originalSender ! findContributorsForRepos(organization, repos)
			context.unbecome()
	}
	
	val authorizationName = "Authorization"
	val config = ConfigFactory.load()
	val envToken = config.getString("github.token")
	
	def checkNumberOfPages(organization: String) = {
		val responseFuture = Http().singleRequest(HttpRequest(uri = s"https://api.github.com/orgs/$organization/repos",
			headers = List(RawHeader(authorizationName, envToken))))
		
		responseFuture.map {
			case HttpResponse(StatusCodes.OK, headers, entity, _) =>
				val linkHeader = headers.collect {
					case Link(x) => x
				}
				entity.discardBytes()
				linkHeader.flatten
					.filter(_.params.contains(LinkParams.last))
					.map(_.uri)
					.map(_.query().value)
					.toList.headOption.getOrElse("1").toInt
		}.pipeTo(self)
	}
	
	def fetchRepos(organization: String, pagesAmount: Int) = {
		val serverRequests = for {
			i <- 1 until pagesAmount + 1
		} yield HttpRequest(
			uri = s"https://api.github.com/orgs/$organization/repos?page=$i",
			headers = List(RawHeader(authorizationName, envToken))
		)
		
		Source(serverRequests)
			.mapAsync(10)(runRequest[Repo])
			.runWith(Sink.seq)
			.map(_.flatten)
			.pipeTo(self)
	}
	
	def findContributorsForRepos(organization: String, repos: Seq[Repo]) = {
		val serverRequests = repos.map(repo =>
			HttpRequest(
				uri = s"https://api.github.com/repos/$organization/${repo.name}/contributors",
				headers = List(RawHeader(authorizationName, envToken))
			)
		).toList
		
		val contributors = Source(serverRequests)
			.mapAsync(5)(runRequest[Contributor])
			.runWith(Sink.seq)
		
		contributors.map(_.flatten
			.groupBy(_.login)
			.map(k => k._1 -> k._2.map(_.contributions))
			.map(k => k._1 -> k._2.sum)
			.flatMap(k => List(Contributor(k._1, k._2)))
			.toSeq.sortBy(_.contributions).reverse
		)
	}
	
	def runRequest[T](req: HttpRequest)(implicit reader: JsonReader[T]) = {
		Http().singleRequest(req).flatMap(
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