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
package paper

import common.{
  Logger,
  UserInfo
}

import permission.{
  Role,
  Author,
  Reviewer
}

import couch.{
  Paper,
  PaperRole
}

import http.BlueHttpException

import gnieh.sohva.control.CouchClient

import spray.http.{
  HttpHeaders,
  StatusCodes
}

import spray.routing.Route

/** Returns the paper roles
 *
 *  @author Lucas Satabin
 */
trait GetPaperRoles {
  this: CoreApi =>

  def getPaperRoles(paperId: String): Route = withRole(paperId) {
    case Author | Reviewer =>
      // only authenticated users may see other people information
      withEntityManager("blue_papers") { paperManager =>
        onSuccess {
          for(roles <- paperManager.getComponent[PaperRole](paperId))
            yield roles match {
              // we are sure that the paper has a revision because it comes from the database
              case Some(roles) =>
                roles
              case None =>
                throw new BlueHttpException(
                  StatusCodes.NotFound,
                  "not_found",
                  s"Paper $paperId not found")
            }
        } { roles =>
          respondWithHeader(HttpHeaders.ETag(roles._rev.get)) {
            complete(Map("authors" -> roles.authors.users, "reviewers" -> roles.reviewers.users))
          }
        }
      }

    case _ =>
      throw new BlueHttpException(
        StatusCodes.Forbidden,
        "no_sufficient_rights",
        "Only authors or reviewers may see paper roles")
  }

}
