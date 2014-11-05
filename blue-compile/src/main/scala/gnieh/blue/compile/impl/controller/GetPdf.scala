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
import couch._

import resource._

import java.io.FileInputStream

import spray.routing.Route

import spray.http.{
  StatusCodes,
  MediaTypes
}

import spray.httpx.marshalling.BasicMarshallers

trait GetPdf {
  this: CompilationApi =>

  def getPdf(paperId: String): Route = withRole(paperId) {
    case Author | Reviewer =>

      import FileUtils._

      val pdfFile = paperConfig.buildDir(paperId) / s"main.pdf"

      if(pdfFile.exists)
        withEntityManager("blue_papers") { paperManager =>
          onSuccess(paperManager.getComponent[Paper](paperId)) {
            case Some(Paper(_, name, _)) =>
              val array = managed(new FileInputStream(pdfFile)) acquireAndGet { pdf =>
                Iterator.continually(pdf.read).takeWhile(_ != -1).map(_.toByte).toArray
              }
              respondWithMediaType(MediaTypes.`application/pdf`) {
                respondWithFileName(s"$name.pdf") {
                  import BasicMarshallers._
                  complete(array)
                }
              }

            case None =>
              complete(
                StatusCodes.NotFound,
                ErrorResponse(
                  "not_found",
                  s"No paper data for paper $paperId"))

          }
        }
      else
        complete(
          StatusCodes.NotFound,
          ErrorResponse(
            "not_found",
            "compiled paper $paperId not found"))

    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors and reviewers may see compiled paper"))

  }

}

