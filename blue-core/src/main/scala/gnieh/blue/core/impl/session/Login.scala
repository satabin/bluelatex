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
package session

import com.typesafe.config.Config

import http._
import common._

import scala.util.{
  Try,
  Success
}

import gnieh.sohva.control.CouchClient

import net.liftweb.json.JBool

import spray.routing.Route

import spray.http.StatusCodes

import spray.httpx.unmarshalling.FormDataUnmarshallers

/** Log the user in.
 *  It delegates to the CouchDB login system and keeps track of the CouchDB cookie
 *
 *  @author Lucas Satabin
 */
trait Login {
  this: CoreApi =>

  import FormDataUnmarshallers._

  val login: Route = formFields('username.?, 'password.?) {
    case (Some(username), Some(password)) =>
      withCouch { session =>
        onSuccess(session.login(username, password)) {
          case true  =>
            // save the current user name in the session
            cookieSession() { (id, map) =>
              val updated =
                map.get(SessionKeys.Username).collect {
                  case s: StringSet => s
                }.map(peers => map.updated(SessionKeys.Username, username)).getOrElse(map)
              updateSession(id, updated) {
                complete(JBool(true))
              }
            }
          case false =>
            complete(
              StatusCodes.Unauthorized,
              ErrorResponse(
                "unable_to_login",
                "Wrong username and/or password"))

        }
      }
    case (_, _) =>
      complete(
        StatusCodes.BadRequest,
        ErrorResponse(
          "unable_to_login",
          "Missing login information"))
  }

}
