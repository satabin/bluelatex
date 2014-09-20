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

import java.io.FileInputStream

import resource._

import http._
import common._
import permission._

import scala.util.Try

import gnieh.sohva.control.CouchClient

import spray.http.{
  MediaTypes,
  StatusCodes
}

import spray.routing.Route

/** Retrieves some resource associated to the paper.
 *
 *  @author Lucas Satabin
 */
trait GetResource {
  this: CoreApi =>

  def getResource(paperId: String, resourceName: String): Route = withRole(paperId) {
    case Author =>
      // only authors may get a resource
      val file = paperConfig.resource(paperId, resourceName)

      if (file.exists) {
        // returns the resource file
        val array = managed(new FileInputStream(file)).acquireAndGet { fis =>
          val length = fis.available
          val ar = new Array[Byte](length)
          fis.read(ar)

          ar
        }

        import FileUtils._

        val mime = MediaTypes.forExtension(file.extension.toLowerCase).getOrElse(MediaTypes.`application/octet-stream`)

        respondWithMediaType(mime) {
          complete(array)
        }
      } else {
        // resource not found => error 404
        throw new BlueHttpException(
          StatusCodes.NotFound,
          "unknown_resource",
          s"Unable to find resource $resourceName for paper $paperId")
      }
    case _ =>
      throw new BlueHttpException(
        StatusCodes.Forbidden,
        "no_sufficient_rights",
        "Only authors may get resources")
  }

}
