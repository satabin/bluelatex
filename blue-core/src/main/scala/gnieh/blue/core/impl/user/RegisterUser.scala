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

import org.osgi.framework.BundleContext

import java.util.Calendar

import scala.util.{
  Try,
  Success,
  Failure,
  Random
}

import gnieh.sohva.async.{
  CouchClient,
  Session
}
import gnieh.sohva.async.entities.EntityManager
import gnieh.sohva.{
  SohvaException,
  ConflictException
}

import spray.routing.Route

import spray.http.StatusCodes

import net.liftweb.json.JBool

/** Handle registration of a new user into the system.
 *  When a user is created it is created with a randomly generated password and a password reset
 *  token is created and sent to the user email address, so that he can confirm both his email
 *  address and it account. He then must set a new password.
 *
 *  @author Lucas Satabin
 */
trait RegisterUser {
  this: CoreApi =>

  val registerUser: Route =
    authorize(recaptcha) {
      entity(as[User]) { user =>
          withCouch { userSession =>
            onSuccess {
              couchConfig.asAdmin(userSession.couch) { session =>
                // generate a random password
                (for {
                  password <- session._uuid
                  // first save the standard couchdb user
                  res <- session.users.add(user.name, password, couchConfig.defaultRoles)
                } yield res) recover {
                  case ConflictException(_) =>
                    logWarn(s"User ${user.name} already exists")
                    complete(
                      StatusCodes.Conflict,
                      ErrorResponse(
                        "unable_to_register",
                        s"The user ${user.name} already exists"))
                }
              }
            } {
              case true =>
                  onSuccess {
                    couchConfig.asAdmin(userSession.couch) { session =>
                      val userManager = new EntityManager(session.database(couchConfig.database("blue_users")))
                      // now the user is registered as standard couchdb user, we can add the \BlueLaTeX specific data
                      val userid = s"org.couchdb.user:${user.name}"
                        (for {
                          () <- userManager.create(userid, Some("blue-user"))
                          user <- userManager.saveComponent(userid, user)
                          _ <- sendEmail(user, session)
                        } yield {
                          import OsgiUtils._
                          // notifiy creation hooks
                          for(hook <- context.getAll[UserRegistered])
                            Try(hook.afterRegister(user.name, userManager))

                        }) recover {
                          case e =>
                            //logWarn(s"Unable to create \\BlueLaTeX user $username")
                            logError(s"Unable to create \\BlueLaTeX user ${user.name}", e)
                            // somehow we couldn't save it
                            // remove the couchdb user from database
                            Try(session.users.delete(user.name))
                            // send error
                            throw new BlueHttpException(
                              StatusCodes.InternalServerError,
                              "unable_to_register",
                              s"Something went wrong when registering the user ${user.name}. Please retry")
                        }
                    }
                  } { _ =>
                    complete(StatusCodes.Created, JBool(true))
                  }
              case false =>
                logWarn(s"Unable to create CouchDB user ${user.name}")
                complete(
                  StatusCodes.InternalServerError,
                  "unable_to_register",
                  s"Something went wrong when registering the user ${user.name}. Please retry")
            }
          }

      }

    }

    private def sendEmail(user: User, session: Session) = {
      // the user is now registered
      // generate the password reset token
      val cal = Calendar.getInstance
      cal.add(Calendar.SECOND, couchConfig.tokenValidity)
      session.users.generateResetToken(user.name, cal.getTime) map { token =>
        logDebug(s"Sending confirmation email to ${user.email}")
        // send the confirmation email
        val email = templates.layout("emails/register",
          "firstName" -> user.first_name,
          "baseUrl" -> config.getString("blue.base-url"),
          "name" -> user.name,
          "token" -> token,
          "validity" -> (couchConfig.tokenValidity / 24 / 3600))
        logDebug(s"Registration email: $email")
        mailAgent.send(user.name, "Welcome to \\BlueLaTeX", email)
      } recover {
        case e =>
          logError(s"Unable to generate confirmation token for user ${user.name}", e)
          throw e
      }
    }

}
