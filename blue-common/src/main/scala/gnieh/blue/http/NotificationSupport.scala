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
package http

import couch.Notifications

import common._

import tiscaf._

import scala.util.{
  Try,
  Success
}

import gnieh.sohva.{
  ViewResult,
  Row
}

/** Mix that trait in to access notifications facilities
 *
 *  @author Lucas Satabin
 */
trait NotificationSupport {
  this: CouchSupport =>

  /** Returns the number of pending notifications for the given username */
  def getNotificationCount(username: String)(implicit talk: HTalk): Try[Int] = {
    val db = database("blue_notifications")
    val view = db.design("lists").view("by_user")
    for(ViewResult(_, _, List(Row(_, _, count, _)), _) <- view.query[String,Int,Nothing](key = Some(username), reduce = true))
      yield count
  }

  /** Returns all the notifications for the given user */
  def getNotifications(username: String)(implicit talk: HTalk): Try[Map[String,String]] = {
    val db = database("blue_notifications")
    val view = db.design("lists").view("by_user")
    for(res <- view.query[String,String,Nothing](key = Some(username), reduce = false))
      yield res.values.toMap
  }

  /** Sends notification message to the given username, according to its own notification settings. */
  def sendNotifications(mailer: MailAgent, templates: Templates, to: String, title: String, description: String)(implicit talk: HTalk): Try[Unit] = {
    // first get notification settings for the user
    val userid = f"org.couchdb.user:$to"
    val manager = entityManager("blue_users")
    manager.getComponent[Notifications](userid).flatMap {
      case Some(Notifications(_, email, api)) =>
        if(email) {
          sendEmail(mailer, templates, to, title, description)
        }
        if(api) {
          for(_ <- database("blue_notifications").createDoc(
            Map(
              "timestamp" -> System.currentTimeMillis,
              "username" -> to,
              "message" -> description)))
            yield ()
        } else {
          Success(())
        }
      case None =>
        manager.saveComponent(userid, defaultNotifications(userid)).flatMap { _ =>
          sendEmail(mailer, templates, to, title, description)
          for(_ <- database("blue_notifications").createDoc(
            Map(
              "timestamp" -> System.currentTimeMillis,
              "username" -> to,
              "message" -> description)))
            yield ()
        }
    }
  }

  private def sendEmail(mailer: MailAgent, templates: Templates, to: String, title: String, message: String): Unit = {
    val email = templates.layout("emails/notification",
      "username" -> to,
      "message" -> message,
      "baseUrl" -> config.getString("blue.base-url"))
    Try(mailer.send(to, title, email))
  }

  private def defaultNotifications(userid: String) =
    Notifications(
      s"$userid:notifications",
      config.getBoolean("blue.notifications.email"),
      config.getBoolean("blue.notifications.api"))


}
