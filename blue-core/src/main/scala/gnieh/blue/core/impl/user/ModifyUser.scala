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
package user

import http._
import couch.User
import common._

import com.typesafe.config.Config

import gnieh.diffson._

import gnieh.sohva.async.entities.EntityManager

import spray.routing.Route

import net.liftweb.json.JBool

import spray.http.{
  StatusCodes,
  HttpHeaders
}

import spray.httpx.unmarshalling.UnmarshallerLifting

/** Handle JSON Patches that modify the user data
 *
 *  @author Lucas Satabin
 */
trait ModifyUser {
  this: CoreApi =>

  def modifyUser(username: String): Route = requireUser() { user =>
    if(username == user.name) {
      // a user can only modify his own data
      optionalHeaderValuePF { case h: HttpHeaders.`If-Match` => h.value } {
        case knownRev @ Some(_) =>
          entity(as[JsonPatch]) { patch =>
            (withEntityManager("blue_users") & withDatabase("blue_users")) { (userManager, db) =>
              // the modification must be sent as a JSON Patch document
              // retrieve the user object from the database
              val userid = s"org.couchdb.user:$username"
              onSuccess {
                userManager.getComponent[User](userid) flatMap {
                  case Some(user) if user._rev == knownRev =>
                    patchAndSave(userManager, patch, userid, user, knownRev)

                  case Some(user) =>
                    // old revision sent
                    throw new BlueHttpException(
                      StatusCodes.Conflict,
                      "conflict",
                      "Old user revision provided")

                  case None =>
                    patchAndSave(userManager, patch, userid, defaultUser(user), None)
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
              "User revision not provided"))

      }
    } else {
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "A user can only modify his own data"))
    }
  }

  private def defaultUser(user: UserInfo) = User(user.name, "New", "User", s"${user.name}@fake.bluelatex.org")

  private def patchAndSave(userManager: EntityManager, patch: JsonPatch, userid: String, user: User, knownRev: Option[String]) = {
    // the revision matches, we can apply the patch
    val user1 = patch(user).withRev(knownRev)
    // and save the new paper data
    // save successfully, return ok with the new ETag
    // we are sure that the revision is not empty because it comes from the database
    userManager.saveComponent(userid, user1) map { u => u._rev.get } recover {
      case e =>
        logError(s"Unable to save new user data for user $userid", e)
        throw new BlueHttpException(
          StatusCodes.NotModified,
          "cannot_save_data",
          "The changes could not be saved, please retry")
      }
  }

}
