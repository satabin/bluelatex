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

import compat.ProtocolTranslator

import common._
import http._
import permission._

import net.liftweb.json.Serialization
import net.liftweb.json._

import scala.concurrent.Future

import spray.routing.Route

import spray.httpx.unmarshalling.BasicUnmarshallers
import spray.httpx.LiftJsonSupport

import spray.http.StatusCodes

/** Legacy compatibility let, allowing mobwrite clients to synchronize papers
 *  with the new implementation.
 *  This let converts from the legacy protocol to the new one before sending the commands to the
 *  synchronization server, and convert it back to the old protocol before sending it back
 *  to the client.
 *
 *  It assumes that the client respects following conventions:
 *   - user name (sent via `F:`) must be of the form `<paper_id>/<file>`
 *
 *  @author Lucas Satabin
 */
trait SynchronizeCompat extends LiftJsonSupport {
  this: SyncApi =>

  import BasicUnmarshallers._

  abstract override implicit def liftJsonFormats =
    super.liftJsonFormats +
    new SyncSessionSerializer +
    new SyncCommandSerializer +
    new SyncActionSerializer +
    new EditSerializer

  def synchronizeCompat(paperId: String): Route = withRole(paperId) {
    case Author =>
      // only authors may modify the paper content
      entity(as[String]) { data =>

        logInfo("mobwrite compatibility let called")

        onSuccess {
          val sessions =
            for(syncSession <- ProtocolTranslator.mobwrite2bluelatex(paperId, data))
              yield {
                val sessionText = Serialization.write[SyncSession](syncSession)
                synchroServer.session(sessionText) recover {
                  case exn =>
                    logError(s"Could not process synchronization session for paper $paperId", exn)
                    ""
                }
              }
          // Convert mobwrite protocol to \BlueLaTeX's
          Future.fold(sessions)(new StringBuilder) { (acc, result) =>
            val respSyncSession = Serialization.read[SyncSession](result)
            acc.append(ProtocolTranslator.bluelatex2mobwrite(respSyncSession)._2)
          }
        } { result =>
          // Write answer back to client
          if (!result.isEmpty) {
            complete(result.toString)
          } else {
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
