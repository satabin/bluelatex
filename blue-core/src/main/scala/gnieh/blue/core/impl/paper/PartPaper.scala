/*
 * This file is part of the \BlueLaTeX project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gnieh.blue
package core
package impl
package paper

import http._
import common._
import permission._

import com.typesafe.config.Config

import akka.actor.ActorSystem

import scala.util.Try

import gnieh.sohva.control.CouchClient

import net.liftweb.json.JBool

import spray.routing.Route

import spray.http.StatusCodes

/** Notify the system that the user left a given paper
 *
 *  @author Lucas Satabin
 */
trait PartPaper {
  this: CoreApi =>

  type StringSet = Set[String]

  def partPaper(paperId: String, peerId: String): Route = withRole(paperId) {
    case Author | Reviewer =>
      // remove this peer from the current session
      cookieSession() { (id, map) =>
        val updated =
          map.get(SessionKeys.Peers).collect {
            case s: StringSet => s
          }.map(peers => map.updated(SessionKeys.Peers, peers - peerId)).getOrElse(map)
        updateSession(id, map) {

          system.eventStream.publish(Part(peerId, Some(paperId)))

          complete(JBool(true))
        }
      }

    case _ =>
      complete(
        StatusCodes.Unauthorized,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors and reviewers may leave a paper"))
  }

}
