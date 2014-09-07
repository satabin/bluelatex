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

import scala.io.{
  Source,
  Codec
}

import resource._

import java.nio.charset.CodingErrorAction

import spray.routing.Route

import spray.http.{
  StatusCodes,
  MediaTypes
}

object GetLog {

  val codec = Codec.UTF8.onMalformedInput(CodingErrorAction.REPLACE)

}

trait GetLog {
  this: CompilationApi =>

  def getLog(paperId: String): Route = withRole(paperId) {
    case Author =>

      import FileUtils._

      val logFile = paperConfig.buildDir(paperId) / s"main.log"

      if(logFile.exists) {
        val text = managed(Source.fromFile(logFile)(GetLog.codec)) acquireAndGet { log =>
          log.mkString.getBytes("UTF-8")
        }

        respondWithMediaType(MediaTypes.`text/plain`) {
          respondWithFileName(logFile.getName) {
            complete(text)
          }
        }
      } else {
        complete(
          StatusCodes.NotFound,
          ErrorResponse(
            "not_found",
            "compilation log for paper $paperId not found"))
      }

    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors may see compilation results"))

  }

}

