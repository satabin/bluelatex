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

import tiscaf._

import gnieh.sohva.control.CouchClient
import gnieh.sohva.{
  DbResult,
  OkResult
}

import scala.util.{
  Try,
  Success
}

class DeleteNotificationsLet(val couch: CouchClient, config: Config, logger: Logger) extends SyncBlueLet(config, logger) with SyncAuthenticatedLet {

  def authenticatedAct(user: UserInfo)(implicit talk: HTalk): Try[Unit] =
    talk.readJson[List[String]] match {
      case Some(ids) =>
        val db = database("blue_notifications")
        val view = db.design("lists").view("by_user")
        for {
          result <- view.query[String, String, Nothing](key = Some(user.name), reduce = false)
          toDelete = ids.union(result.values.map(_._2))
          results <- db.deleteDocs(toDelete)
        } yield talk.writeJson(allOk(results))
      case None =>
          Success(
            talk
              .setStatus(HStatus.NotModified)
              .writeJson(ErrorResponse("nothing_to_do", "No notifications sent")))
    }

  def allOk(results: List[DbResult]): Boolean =
    results.forall {
      case OkResult(ok, _, _) => ok
      case _                  => false
    }

}
