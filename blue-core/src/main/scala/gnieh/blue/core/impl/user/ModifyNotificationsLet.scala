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

import gnieh.diffson._

import scala.util.{
  Try,
  Success,
  Failure
}

import gnieh.sohva.control.CouchClient

/** Handle JSON Patches that modify the user notification settings.
 *
 *  @author Lucas Satabin
 */
class ModifyNotificationsLet(
  username: String,
  val couch: CouchClient,
  config: Config,
  logger: Logger) extends SyncBlueLet(config, logger) with SyncAuthenticatedLet {

  def authenticatedAct(user: UserInfo)(implicit talk: HTalk): Try[Unit] =
    if(username == user.name) {
      // a user can only modify his own data
      (talk.req.octets, talk.req.header("if-match")) match {
        case (Some(octets), knownRev @ Some(_)) =>
          val db = database(blue_users)
          // the modification must be sent as a JSON Patch document
          // retrieve the user object from the database
          val manager = entityManager("blue_users")
          val userid = s"org.couchdb.user:$username"
          manager.getComponent[Notifications](userid) flatMap {
            case Some(notifications) if notifications._rev == knownRev =>
              patchAndSave(userid, notifications, knownRev)

            case Some(user) =>
              // old revision sent
              Success(
                talk
                  .setStatus(HStatus.Conflict)
                  .writeJson(ErrorResponse("conflict", "Old notifications revision provided")))

            case None =>
              patchAndSave(userid, defaultNotifications(userid), None)
          }

        case (None, _) =>
          // nothing to do
          Success(
            talk
              .setStatus(HStatus.NotModified)
              .writeJson(ErrorResponse("nothing_to_do", "No changes sent")))

        case (_, None) =>
          // known revision was not sent, precondition failed
          Success(
            talk
              .setStatus(HStatus.Conflict)
              .writeJson(ErrorResponse("conflict", "Notifications revision not provided")))
      }
    } else {
      Success(
        talk
          .setStatus(HStatus.Forbidden)
          .writeJson(ErrorResponse("no_sufficient_rights", "A user can only modify his own data")))
    }

    private def defaultNotifications(userid: String) =
      Notifications(
        s"$userid:notifications",
        config.getBoolean("blue.notifications.email"),
        config.getBoolean("blue.notifications.api"),
        config.getBoolean("blue.notifications.new-papers"),
        Nil)

    private def patchAndSave(userid: String, notifications: Notifications, knownRev: Option[String])(implicit talk: HTalk) =
      talk.readJson[JsonPatch] match {
        case Some(patch) =>
          // the revision matches, we can apply the patch
          val notifications1 = patch(notifications).withRev(knownRev)
          // and save the new notification settings
          (for(n <- entityManager("blue_users").saveComponent(userid, notifications1))
            yield {
                // save successfully, return ok with the new ETag
                // we are sure that the revision is not empty because it comes from the database
                talk.writeJson(true, n._rev.get)
            }) recover {
              case e =>
                logError(s"Unable to save new notification settings for user $userid", e)
                talk
                  .setStatus(HStatus.NotModified)
                  .writeJson(ErrorResponse("cannot_save_data", "The changes could not be saved, please retry"))
            }
        case None =>
          // nothing to do
          Success(
            talk
              .setStatus(HStatus.NotModified)
              .writeJson(ErrorResponse("nothing_to_do", "No changes sent")))
      }

}

