package io.scalac.actors

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.{JsonFraming, Keep, Sink}
import com.typesafe.config.ConfigFactory
import io.scalac.JsonProtocol
import io.scalac.Model.{Contributor, Repo}
import io.scalac.Server.logger
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object GitHubActor {
	case class FetchContributors(organization: String)
}

class GitHubActor extends Actor with JsonProtocol with ActorLogging {
	
	import io.scalac.Global._
	import GitHubActor._
	import system.dispatcher
	
	override def receive: Receive = {
		case FetchContributors(organization) =>
			val repos = fetchRepos(organization)
			log.info(s"Fetched repositories: $repos")
			sender() ! findContributorsForRepos(organization, repos)
	}
	
	val authorizationName = "Authorization"
	val config = ConfigFactory.load()
	val envToken = config.getString("github.token")
	
	def fetchRepos(organization: String) = {
		val responseFuture = Http().singleRequest(HttpRequest(uri = s"https://api.github.com/orgs/$organization/repos",
			headers = List(RawHeader(authorizationName, envToken))))
		
		val reposFuture = responseFuture.flatMap(
			_.entity
				.dataBytes
				.via(JsonFraming.objectScanner(Int.MaxValue))
				.map(_.utf8String)
				.map(_.parseJson.convertTo[Repo])
				.toMat(Sink.seq)(Keep.right)
				.run()
		)
		
		Await.result(reposFuture, 20 seconds)
	}
	
	def findContributorsForRepos(organization: String, repos: Seq[Repo]) = {
		val contributors = for {
			repo <- repos
		} yield findContributors(organization, repo)
		
		Future.sequence(contributors).map(_.flatten
			.groupBy(_.login)
			.map(k => k._1 -> k._2.map(_.contributions))
			.map(k => k._1 -> k._2.sum)
			.flatMap(k => List(Contributor(k._1, k._2)))
			.toSeq.sortBy(_.contributions).reverse
		)
	}
	
	def findContributors(organization: String, repo: Repo) = {
		log.info(s"Fetching contributors for repo: $repo")
		val responseFuture = Http().singleRequest(HttpRequest(uri = s"https://api.github.com/repos/$organization/${repo.name}/contributors",
			headers = List(RawHeader(authorizationName, envToken))))
		responseFuture.flatMap(
			_.entity
				.dataBytes
				.via(JsonFraming.objectScanner(Int.MaxValue))
				.map(_.utf8String)
				.map(_.parseJson.convertTo[Contributor])
				.toMat(Sink.seq)(Keep.right)
				.run()
		)
	}
}