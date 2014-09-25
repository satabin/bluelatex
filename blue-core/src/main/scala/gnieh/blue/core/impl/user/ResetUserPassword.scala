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
import common._

import spray.routing.Route

import spray.http.StatusCodes

import net.liftweb.json.JBool

import spray.httpx.unmarshalling.FormDataUnmarshallers

/** Performs the password reset action for a given user.
 *
 *  @author Lucas Satabin
 */
trait ResetUserPassword {
  this: CoreApi =>

  import FormDataUnmarshallers._

  def resetUserPassword(username: String): Route = formFields('reset_token.?, 'new_password1.?, 'new_password2.?) {
    case (Some(token), Some(password1), Some(password2)) if password1 == password2 =>
      withCouch { userSession =>
        // all parameters given, and passwords match, proceed
        onSuccess {
          couchConfig.asAdmin(userSession.couch) { sess =>
            sess.users.resetPassword(username, token, password1) map {
              case true =>
                ()
              case false =>
                throw new BlueHttpException(
                  StatusCodes.InternalServerError,
                  "unable_to_reset",
                  "Cannot perform password reset")
            }
          }
        } { _ =>
          complete(JBool(true))
        }
      }
    case (t, p1, p2) =>
      // a parameter is missing or password do not match
      complete(
        StatusCodes.BadRequest,
        ErrorResponse(
          "unable_to_reset",
          "Wrong parameters"))
  }

}
