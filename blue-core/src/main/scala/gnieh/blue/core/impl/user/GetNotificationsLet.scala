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

import couch.Notification
import common._

import com.typesafe.config.Config

import tiscaf._

import gnieh.sohva.control.CouchClient

import scala.util.{
  Try,
  Success
}

/** Returns the List or count of notifications for the given user.
 *
 *  @author Lucas Satabin
 */
class GetNotificationsLet(val couch: CouchClient, config: Config, logger: Logger) extends SyncBlueLet(config, logger) with SyncAuthenticatedLet {

  def authenticatedAct(user: UserInfo)(implicit talk: HTalk): Try[Unit] = {
    val count = talk.req.param("count").map(java.lang.Boolean.parseBoolean(_)).getOrElse(false)
    if(count) {
      for(c <- getNotificationCount(user.name))
        yield talk.writeJson(c)
    } else {
      for(n <- getNotifications(user.name))
        yield talk.writeJson(n)
    }
  }

}
