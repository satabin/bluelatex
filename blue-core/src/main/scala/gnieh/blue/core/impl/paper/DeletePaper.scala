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

import couch._
import common._
import http._
import permission._

import java.util.UUID
import java.io.{
  File,
  FileWriter
}

import net.liftweb.json.JBool

import resource._

import scala.sys.process._

import scala.util.{
  Try,
  Success
}

import gnieh.sohva.control.CouchClient


import spray.routing.Route

import spray.http.StatusCodes

/** Delete an existing paper.
 *
 *  @author Lucas Satabin
 */
trait DeletePaper {
  this: CoreApi =>

  def deletePaper(paperId: String): Route =
    withEntityManager("blue_papers") { paperManager =>
      withRole(paperId) {
        case Author =>
          // only authors may delete a paper
          // first delete the paper files
          import FileUtils._

          // delete the paper directory if it exists
          val paperDir = paperConfig.paperDir(paperId)
          val continue =
            if(paperDir.exists)
              paperDir.deleteRecursive()
            else
              true

          if(continue) {
            import OsgiUtils._

            onSuccess {
              paperManager.deleteEntity(paperId) map {
                case true =>
                  // notifiy deletion hooks
                  for(hook <- context.getAll[PaperDeleted])
                    Try(hook.afterDelete(paperId, paperManager))

                  true

                case false =>
                  throw new BlueHttpException(
                    StatusCodes.InternalServerError,
                    "cannot_delete_paper",
                    "Unable to delete the paper database")
              }
            } { res =>
              complete(JBool(res))
            }

          } else {
            complete(
              StatusCodes.InternalServerError,
              ErrorResponse(
                "cannot_delete_paper",
                "Unable to delete the paper files"))
          }

        case _ =>
          complete(
            StatusCodes.Forbidden,
            ErrorResponse(
              "no_sufficient_rights",
              "Only authors may delete a paper"))
      }

    }

}
