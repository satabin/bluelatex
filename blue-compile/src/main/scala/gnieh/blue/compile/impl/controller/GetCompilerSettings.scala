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
package compile
package impl

import http._
import common._
import permission._

import gnieh.sohva.async.CouchClient

import scala.util.{
  Success,
  Failure
}

import spray.routing.Route

import spray.http.{
  StatusCodes,
  HttpHeaders
}

/** Handle request that want to access the compiler data
 *
 *  @author Lucas Satabin
 */
trait GetCompilerSettings {
  this: CompilationApi =>

  def getCompilerSettings(paperId: String): Route = withRole(paperId) {
    case Author =>
      // only authors users may see other compiler settings
      withEntityManager("blue_papers") { paperManager =>
        onComplete(paperManager.getComponent[CompilerSettings](paperId)) {
          // we are sure that the settings has a revision because it comes from the database
          case Success(Some(settings)) =>
            respondWithHeader(HttpHeaders.ETag(settings._rev.get)) {
              complete(settings)
            }
          case Success(None) =>
            complete(
              StatusCodes.NotFound,
              ErrorResponse(
                "not_found",
                s"No compiler for paper $paperId found"))
          case Failure(e) =>
            logError(s"Error while retrieving compiler settings for paper $paperId", e)
            complete(
              StatusCodes.InternalServerError,
              ErrorResponse(
                "cannot_get_compiler",
                s"No compiler for paper $paperId found"))
        }
      }

    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors may see compiler settings"))

  }

}

