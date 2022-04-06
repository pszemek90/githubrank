package io.scalac.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.pattern.pipe
import akka.stream.scaladsl.{JsonFraming, Keep, Sink, Source}
import io.scalac.JsonProtocol
import io.scalac.Model.{Contributor, Repo}
import spray.json._

import scala.concurrent.duration.DurationInt

class RepoFetcherActor extends Actor with ActorLogging with JsonProtocol{
	import GitHubActor._
	import io.scalac.Global._
	
	var requestSent = 0
	
	override def receive: Receive = {
		case FetchRepos(organization, numberOfPages) =>
			log.info(s"Fetching repos for $organization")
			fetchRepos(organization, numberOfPages)
			context.become(reposFetched(organization, sender()))
	}
	
	def reposFetched(organization: String, originalSender: ActorRef): Receive = {
		case ReposFetched(repos) =>
			log.info(s"Sending ${repos.size} repos for contributor actors")
			log.info(s"Fetched repos: $repos")
			Source(repos)
				.throttle(200, 1.second)
				.runWith(Sink.foreach(sendToContributorActor))
			context.become(aggregateContributors(Seq(), 1, repos.size, originalSender))
			def sendToContributorActor(repo: Repo): Unit= {
				val contributorActor = context.actorOf(Props[ContributorActor])
				contributorActor ! FetchContributorsForRepo(repo, organization)
			}
	}
	
	def aggregateContributors(aggregate: Seq[Contributor], messagesReceived: Int, expectedSize: Int, originalSender: ActorRef): Receive = {
		case ContributorsFetched(contributors) =>
			if(messagesReceived == expectedSize) {
				log.info(s"Fetched ${messagesReceived} packages of $expectedSize")
				sendResponse(aggregate, originalSender)
				context.stop(self)
			} else {
				log.info(s"Fetched $messagesReceived packages of $expectedSize")
				context.become(aggregateContributors(aggregate ++ contributors, messagesReceived + 1, expectedSize, originalSender))
			}
	}
	
	def sendResponse(contributors: Seq[Contributor], originalSender: ActorRef): Unit = {
		log.info("Sending aggregated contributors to original sender")
		val sortedContributors = contributors
			.groupBy(_.login)
			.map(k => k._1 -> k._2.map(_.contributions))
			.map(k => k._1 -> k._2.sum)
			.flatMap(k => List(Contributor(k._1, k._2)))
			.toSeq.sortBy(_.contributions).reverse
		
		originalSender ! ContributorsFetched(sortedContributors)
	}
	
	def fetchRepos(organization: String, numberOfPages: Int) = {
		val serverRequests = for {
			i <- 1 until numberOfPages + 1
		} yield HttpRequest(
			uri = s"https://api.github.com/orgs/$organization/repos?page=$i",
			headers = List(RawHeader(authorizationName, envToken))
		)
		
		Source(serverRequests)
			.mapAsyncUnordered(200)(runRequest[Repo])
			.runWith(Sink.seq)
			.map(_.flatten)
			.map(ReposFetched)
			.pipeTo(self)
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
