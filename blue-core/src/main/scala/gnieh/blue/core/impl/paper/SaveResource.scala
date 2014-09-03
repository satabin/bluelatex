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

import com.typesafe.config.Config

import java.io.{
  FileOutputStream,
  ByteArrayOutputStream
}

import resource._

import http._
import common._
import permission._

import scala.util.Try

import gnieh.sohva.control.CouchClient

import spray.routing.{
  Route,
  RejectionHandler,
  RequestEntityExpectedRejection
}

import spray.http.StatusCodes

import net.liftweb.json.JBool

/** Saves some resource associated to the paper.
 *
 *  @author Lucas Satabin
 */
trait SaveResource {
  this: CoreApi =>

  val contentHandler = RejectionHandler {
    case List(RequestEntityExpectedRejection, _*) =>
      complete(
        StatusCodes.NoContent,
        ErrorResponse(
          "no_content",
          "No file sent to save"))
  }

  def saveResource(paperId: String, resourceName: String): Route = withRole(paperId) {
    case Author =>
      // only authors may upload a resource
      handleRejections(contentHandler) {
        entity(as[Array[Byte]]) { resourceFile =>

          val file = paperConfig.resource(paperId, resourceName)

          // if file does not exist, create it
          if (!file.exists)
            file.createNewFile

          // save the resource to disk
          // save it in the resource directory
          for(fos <- managed(new FileOutputStream(file))) {
            fos.write(resourceFile)
          }

          complete(JBool(true))

        }
      }

    case _ =>
        complete(
          StatusCodes.Forbidden,
            ErrorResponse(
              "no_sufficient_rights",
              "Only authors may upload resources"))
  }

}
