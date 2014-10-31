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

import gnieh.sohva.control.CouchClient

import scala.io.Source

import scala.util.Try

import spray.http.{
  StatusCodes,
  HttpHeaders
}

import spray.routing.Route

/** Returns the user data
 *
 *  @author Lucas Satabin
 */
trait GetUserInfo {
  this: CoreApi =>

  def getUserInfo(username: String): Route = requireUser() { _ =>
    withEntityManager("blue_users") { userManager =>
      // only authenticated users may see other people information
      onSuccess(userManager.getComponent[User](s"org.couchdb.user:$username")) {
        // we are sure that the user has a revision because it comes from the database
        case Some(user) =>
          respondWithHeader(HttpHeaders.ETag(user._rev.get)) {
            complete(user)
          }
        case None =>
          complete(
            StatusCodes.NotFound,
            ErrorResponse(
              "not_found",
              s"No user named $username found"))
      }
    }
  }

}
