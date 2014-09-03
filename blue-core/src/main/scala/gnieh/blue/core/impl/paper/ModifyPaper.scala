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
import couch.Paper
import common._
import permission._

import gnieh.diffson._

import spray.routing.Route

import net.liftweb.json.JBool

import spray.http.{
  StatusCodes,
  HttpHeaders
}

import spray.httpx.unmarshalling.UnmarshallerLifting

/** Handle JSON Patches that modify paper data such as paper name
 *
 *  @author Lucas Satabin
 */
trait ModifyPaper {
  this: CoreApi =>

  import UnmarshallerLifting._

  def modifyPaper(paperId: String): Route = withRole(paperId) {
    case Author =>
      // only authors may modify this list
      optionalHeaderValuePF { case h: HttpHeaders.`If-Match` => h.value } {
        case knownRev @ Some(_) =>
          entity(as[JsonPatch]) { patch =>
            withEntityManager("blue_papers") { paperManager =>
              // the modification must be sent as a JSON Patch document
              // retrieve the paper object from the database
              onSuccess {
                paperManager.getComponent[Paper](paperId) flatMap {
                  case Some(paper) if paper._rev == knownRev =>
                    // the revision matches, we can apply the patch
                    val paper1 = patch(paper).withRev(knownRev)
                    // and save the new paper data
                    for(p <- paperManager.saveComponent(paperId, paper1))
                      // save successfully, return ok with the new ETag
                      // we are sure that the revision is not empty because it comes from the database
                      yield p._rev.get

                  case Some(_) =>
                    // nothing to do
                    throw new BlueHttpException(
                      StatusCodes.Conflict,
                      "conflict",
                      "Old paper info revision provided")

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
          complete(
            StatusCodes.Conflict,
            ErrorResponse(
              "conflict",
              "Paper revision not provided"))
      }

    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors may modify the paper data"))
  }

}
