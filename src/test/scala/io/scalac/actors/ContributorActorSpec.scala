package io.scalac.actors

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKitBase
import io.scalac.Global
import io.scalac.Model.Repo
import org.scalatest.wordspec.AnyWordSpec

class ContributorActorSpec extends AnyWordSpec with TestKitBase{
	import io.scalac.Global._
	import io.scalac.actors.GitHubActor._
	
	override implicit def system: ActorSystem = Global.system
	
	"Contributor actor" should {
		"send back message of type ContributorsFetched" in {
			val contributorActor = system.actorOf(Props[ContributorActor])
			contributorActor ! FetchContributorsForRepo(Repo("zio"), "zio")
			expectMsgType[ContributorsFetched]
		}
	}
}

object ContributorActorSpec {
}
