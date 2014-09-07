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
package compile
package impl

import http._
import common._
import permission._

import gnieh.diffson.JsonPatch

import spray.routing.Route

import net.liftweb.json.JBool

import spray.http.{
  StatusCodes,
  HttpHeaders
}

import spray.httpx.unmarshalling.UnmarshallerLifting

/** Handle JSON Patches that modify the compiler data
 *
 *  @author Lucas Satabin
 */
trait ModifyCompiler {
  this: CompilationApi =>

  import UnmarshallerLifting._

  def modifyCompiler(paperId: String): Route = withRole(paperId) {
    case Author =>
      optionalHeaderValuePF { case h: HttpHeaders.`If-Match` => h.value } {
        case knownRev @ Some(_) =>
          entity(as[JsonPatch]) { patch =>
            withEntityManager("blue_papers") { paperManager =>
              // the modification must be sent as a JSON Patch document
              // retrieve the settings object from the database
              onSuccess {
                paperManager.getComponent[CompilerSettings](paperId) flatMap {
                  case Some(settings) if settings._rev == knownRev =>
                    // the revision matches, we can apply the patch
                    val settings1 = patch(settings).withRev(knownRev)
                    // and save the new compiler data
                    for(s <- paperManager.saveComponent(paperId, settings1))
                      yield {
                        dispatcher ! Forward(paperId, settings1)
                        // save successfully, return ok with the new ETag
                        // we are sure that the revision is not empty because it comes from the database
                        s._rev.get
                      }

                  case Some(_) =>
                    // conflict
                    throw new BlueHttpException(
                      StatusCodes.Conflict,
                      "conflict",
                      "Old compilet revision provided")

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
              "Settings revision not provided"))

      }

    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors may change compiler settings"))
  }

}

