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

import com.typesafe.config.Config

import java.util.Calendar

import scala.util.{
  Try,
  Success
}

import gnieh.sohva.control.CouchClient


import spray.routing.Route

/** Generates a password reset token and send it per email.
 *
 *  @author Lucas Satabin
 */
trait GeneratePasswordReset {
  this: CoreApi =>

  // if the user is authenticated, he cannot generate the password reset token for other people
  def generatePasswordReset(username: String): Route =
    if(user.name == username)
      unauthenticatedAct(talk)
    else
      Success(
        talk
          .setStatus(HStatus.Forbidden)
          .writeJson(ErrorResponse("unable_to_generate", "Authenticated users cannot ask password reset for other people")))
 
  override def unauthenticatedAct(implicit talk: HTalk): Try[Unit] =
    // generate reset token to send the link in an email
    couchConfig.asAdmin(couch) { sess =>
      val cal = Calendar.getInstance
      cal.add(Calendar.SECOND, couchConfig.tokenValidity)
      for(token <- sess.users.generateResetToken(username, cal.getTime))
        yield {
          // send the link to reset the password in an email
          val emailText =
            templates.layout(
              "emails/reset",
              "baseUrl" -> config.getString("blue.base-url"),
              "name" -> username,
              "token" -> token,
              "validity" -> (couchConfig.tokenValidity / 24 / 3600))
          mailAgent.send(username, "Password Reset Requested", emailText)
          talk.writeJson(true)
        }
    }
}

