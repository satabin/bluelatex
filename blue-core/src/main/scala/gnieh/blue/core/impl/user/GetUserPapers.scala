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
import couch.{
  User,
  UserRole,
  Paper
}
import common._

import com.typesafe.config.Config

import gnieh.sohva.control.CouchClient

import scala.io.Source

import scala.util.Try


import spray.routing.Route

/** Returns the list of paper a user is involved in, together with his role for this paper.
 *
 *  @author Lucas Satabin
 */
trait GetUserPapers {
  this: CoreApi =>

  def getUserPapers(username: String): Route = requireUser { _ =>
    (withView("blue_papers", "papers", "for") & withCouch) { (view, session) =>
      // only authenticated users may see other people information
      onSuccess(view.query[String, UserRole, Any](key = Some(username))) { res =>
        val roles = res.values
        val result =
          (for {
            (_, UserRole(id, role)) <- roles
          } yield (id, role)).toList

        onSuccess(session.database(blue_papers).getDocsById[Paper](result.map(id => s"${id._1}:core"))) { papers =>
          complete((result zip papers) map {
            case ((id, role), Paper(_, name, created)) => Map("id" -> id, "role" -> role, "name" -> name, "creation_date" -> created)
          })
        }
      }
    }
  }

}
