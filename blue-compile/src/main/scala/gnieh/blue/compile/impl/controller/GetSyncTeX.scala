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

import java.io.FileInputStream

import resource._

import spray.routing.Route

import spray.http.{
  StatusCodes,
  MediaTypes,
  HttpHeaders,
  HttpEncodings
}

import spray.httpx.marshalling.BasicMarshallers

trait GetSyncTeX {
  this: CompilationApi =>

  import FileUtils._

  def getSyncTeX(paperId: String): Route = withRole(paperId) {
    case Author =>
      val syncTeXFile = paperConfig.buildDir(paperId) / s"main.synctex.gz"

      if (!syncTeXFile.exists) {
        complete(
          StatusCodes.NotFound,
          ErrorResponse(
            "not_found",
            "SyncTeX file not found"))
      } else {
        val array = managed(new FileInputStream(syncTeXFile)) acquireAndGet { is =>
          Iterator.continually(is.read).takeWhile(_ != -1). map(_.toByte).toArray
        }

        respondWithHeader(HttpHeaders.`Content-Encoding`(HttpEncodings.gzip)) {
          respondWithMediaType(MediaTypes.`text/plain`) {
            import BasicMarshallers._
            complete(array)
          }
        }
      }

    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors may retrieve SyncTeX data"))

  }

}

