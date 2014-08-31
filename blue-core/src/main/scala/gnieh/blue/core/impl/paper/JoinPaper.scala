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

import net.liftweb.json.JBool

import spray.routing.Route

import spray.http.StatusCodes

/** Notify the system that the user joined a given paper
 *
 *  @author Lucas Satabin
 */
trait JoinPaper {
  this: CoreApi =>

  def joinPaper(paperId: String, peerId: String): Route = withRole(paperId) {
    case Author | Reviewer =>
      system.eventStream.publish(Join(peerId, paperId))
      complete(JBool(true))
    case _ =>
      throw new BlueHttpException(
        StatusCodes.Unauthorized,
        "no_sufficient_rights",
        "Only authors and reviewers may join a paper")
  }

}
