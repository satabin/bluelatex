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
import couch.Notifications
import common._

import com.typesafe.config.Config

import tiscaf._

import gnieh.sohva.control.CouchClient

import scala.util.{
  Try,
  Success
}

/** Returns the notification settings.
 *
 *  @author Lucas Satabin
 */
class GetNotificationSettingsLet(username: String, val couch: CouchClient, config: Config, logger: Logger) extends SyncBlueLet(config, logger) with SyncAuthenticatedLet {

  def authenticatedAct(user: UserInfo)(implicit talk: HTalk): Try[Unit] =
    if(user.name == username) {
      // only authenticated users may see other people information
      val manager = entityManager("blue_users")
      manager.getComponent[Notifications](s"org.couchdb.user:$username") flatMap {
        // we are sure that the user has a revision because it comes from the database
        case Some(nots) => Success(talk.writeJson(nots, nots._rev.get))
        case None       =>
          manager.saveComponent(s"org.couchdb.user:$username", defaultSettings) map { settings =>
            talk.writeJson(settings, settings._rev.get)
          }
      }
    } else {
      Success(
        talk.setStatus(HStatus.Forbidden)
          .writeJson(ErrorResponse("no_sufficient_rights", "Users may only see their own notification settings")))
    }

  val defaultSettings =
    Notifications(
      s"org.couchdb.user:$username:notifications",
      config.getBoolean("blue.notifications.email"),
      config.getBoolean("blue.notifications.api"))

}
