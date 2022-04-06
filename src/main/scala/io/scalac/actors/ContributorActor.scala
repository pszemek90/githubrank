package io.scalac.actors

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{RawHeader, RetryAfterDuration, `Retry-After`}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.pattern.pipe
import akka.stream.scaladsl.{JsonFraming, Keep, Sink}
import io.scalac.JsonProtocol
import io.scalac.Model.{Contributor, Repo}
import spray.json._

import scala.concurrent.duration.DurationLong
import scala.util.{Failure, Success}

class ContributorActor extends Actor with ActorLogging with JsonProtocol {
	
	import GitHubActor._
	import io.scalac.Global._
	
	override def receive: Receive = {
		case FetchContributorsForRepo(repo: Repo, organization: String) =>
			val request = HttpRequest(
				uri = s"https://api.github.com/repos/$organization/${repo.name}/contributors",
				headers = List(RawHeader(authorizationName, envToken))
			)
			val response = Http().singleRequest(request)
			response.onComplete {
				case Success(HttpResponse(StatusCodes.OK, _, entity, _)) =>
					log.info(s"Successful response from github")
					entity.dataBytes
						.via(JsonFraming.objectScanner(Int.MaxValue))
						.map(_.utf8String)
						.map(_.parseJson.convertTo[Contributor])
						.toMat(Sink.seq)(Keep.right)
						.run()
						.map(ContributorsFetched)
						.pipeTo(context.parent)
					context.stop(self)
				case Success(HttpResponse(StatusCodes.Forbidden, headers, entity, _)) =>
					log.info(s"Secondary limit hit")
					entity.discardBytes()
					log.info(s"Headers: $headers")
					// theoretically there should be only one header matching this collect
					val delaySecondsOption = headers.collectFirst {
						case `Retry-After`(x: RetryAfterDuration) => x.delayInSeconds
					}
					delaySecondsOption match {
						case Some(delaySeconds: Long) =>
							system.scheduler.scheduleOnce(delaySeconds.seconds) {
								self ! FetchContributorsForRepo(repo, organization)
							}
						case None =>
							log.info(s"There was no Link header with value for entity: $entity")
							context.parent ! ContributorsFetched(Seq())
							context.stop(self)
					}
				
				case Success(HttpResponse(statusCode, _, entity, _)) =>
					log.info(s"Status code received: $statusCode for request ${request.uri}")
					entity.discardBytes()
					context.parent ! ContributorsFetched(Seq())
					context.stop(self)
				case Failure(ex) => log.info(s"Request ${request.uri} failed with exception: $ex")
					context.stop(self)
			}
	}
}
