package io.scalac

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {
	import Model._
	implicit val repoFormat = jsonFormat1(Repo)
	implicit val contributorFormat = jsonFormat2(Contributor)
}

object Model {
	case class Repo(name: String)
	
	case class Contributor(login: String, contributions: Int)
}
