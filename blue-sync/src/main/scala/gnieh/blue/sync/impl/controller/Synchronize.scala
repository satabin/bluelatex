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
package sync
package impl

import common._
import http._
import permission._

import scala.util.{
  Success,
  Failure
}

import spray.routing.Route

import spray.http.{
  StatusCodes,
  MediaTypes
}

import spray.httpx.unmarshalling.{
  BasicUnmarshallers,
  UnmarshallerLifting
}
import spray.httpx.marshalling.BasicMarshallers

import spray.http.StatusCodes

/** A synchronization request for a paper. Only authors may send this kind of request
 *
 *  @author Lucas Satabin
 */
trait Synchronize {
  this: SyncApi =>

  def synchronize(paperId: String): Route = withRole(paperId) {
    case Author =>
      // only authors may modify the paper content
      entity(as[String]) { data =>
        onComplete(synchroServer.session(data)) {
          case Success(result) =>
            // TODO use `writeJson` once the synchronization server returns
            // a SyncSession instead of a string
            respondWithMediaType(MediaTypes.`application/json`) {
              import BasicMarshallers._
              complete(result)
            }
          case Failure(f) => {
            logError(s"Could not process synchronization session for paper $paperId", f)
            complete(
              StatusCodes.InternalServerError,
              ErrorResponse(
                "sync_error",
                s"Something went wrong when processing synchronization session for $paperId"))
          }
        }
      }
    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors may modify the paper content"))
  }

}
