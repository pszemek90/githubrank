package io.scalac

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{JsonFraming, Keep, Sink}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import spray.json.{DefaultJsonProtocol, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

case class Repo(name: String)

case class Contributor(login: String, contributions: Int)

trait RepoJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {
	implicit val repoFormat = jsonFormat1(Repo)
	implicit val contributorFormat = jsonFormat2(Contributor)
}

object Server extends App with RepoJsonProtocol with LazyLogging {
	implicit val system = ActorSystem()
	implicit val materializer = ActorMaterializer()
	
	import system.dispatcher
	
	val route =
		(path("org" / Segment / "contributors") & get) { organization =>
			complete(fetchRepos(organization))
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
		
		val repos = Await.result(reposFuture, 20 seconds)
		logger.info(s"Fetched repos: $repos")
		
		findContributorsForRepos(organization, repos)
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
		logger.info(s"Fetching contributors for repo: $repo")
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
	
	Http().bindAndHandle(route, "localhost", 8080)
}
