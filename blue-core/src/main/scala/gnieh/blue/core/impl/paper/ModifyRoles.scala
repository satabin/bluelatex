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
import couch.PaperRole
import common._
import permission._

import gnieh.diffson._

import net.liftweb.json.JBool

import spray.routing.Route

import spray.http.{
  HttpHeaders,
  StatusCodes
}

import spray.httpx.unmarshalling.UnmarshallerLifting

/** Handle JSON Patches that add/remove/modify people involved in the given paper
 *
 *  @author Lucas Satabin
 */
trait ModifyRoles {
  this: CoreApi =>

  def modifyRoles(paperId: String): Route = withRole(paperId) {
    case Author =>
      // only authors may modify this list
      optionalHeaderValuePF { case h: HttpHeaders.`If-Match` => h.value } {
        case knownRev @ Some(_) =>
          entity(as[JsonPatch]) { patch =>
            withEntityManager("blue_papers") { paperManager =>
              // the modification must be sent as a JSON Patch document
              // retrieve the paper object from the database
              onSuccess {
                paperManager.getComponent[PaperRole](paperId) flatMap {
                  case Some(roles) if roles._rev == knownRev =>
                    // the revision matches, we can apply the patch
                    val roles1 = patch(Map("authors" -> roles.authors.users, "reviewers" -> roles.reviewers.users))
                    val roles2 =
                      roles.copy(
                        authors = roles.authors.copy(users = roles1("authors")),
                        reviewers = roles.reviewers.copy(users = roles1("reviewers"))).withRev(knownRev)
                    // and save the new paper data
                    for(r <- paperManager.saveComponent(paperId, roles2))
                      // save successfully, return ok with the new ETag
                      // we are sure that the revision is not empty because it comes from the database
                      yield r._rev.get

                  case Some(_) =>
                    // nothing to do
                    throw new BlueHttpException(
                      StatusCodes.Conflict,
                      "conflict",
                      "Old roles revision provided")

                  case None =>
                    // unknown paper
                    throw new BlueHttpException(
                      StatusCodes.NotFound,
                      "nothing_to_do",
                      s"Unknown paper $paperId")

                }
              } { rev =>
                respondWithHeader(HttpHeaders.ETag(rev)) {
                  complete(JBool(true))
                }
              }
            }

          }

        case None =>
          // known revision was not sent, precondition failed
          throw new BlueHttpException(
            StatusCodes.Conflict,
            "conflict",
            "Paper revision not provided")

      }

    case _ =>
      throw new BlueHttpException(
        StatusCodes.Forbidden,
        "no_sufficient_rights",
        "Only authors may modify the paper roles")
  }

}
