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

import org.apache.pdfbox.pdmodel.PDDocument

import resource._

import spray.routing.Route

import spray.http.StatusCodes

import net.liftweb.json.JInt

/** Handle request that ask for the number of pages in the compiled paper.
 *
 *  @author Lucas Satabin
 */
trait GetPages {
  this: CompilationApi =>

  import FileUtils._

  def getPages(paperId: String): Route = withRole(paperId) {
    case Author | Reviewer =>

      // the generated pdf file
      val pdfFile = paperConfig.buildDir(paperId) / s"main.pdf"

      if(pdfFile.exists) {

          managed(PDDocument.load(pdfFile)).map(_.getNumberOfPages).either match {
            case Right(pages) =>
              complete(JInt(pages))
            case Left(errors) =>
              logError(s"Cannot extract number of pages for paper $paperId", errors.head)
              complete(
                StatusCodes.InternalServerError,
                ErrorResponse(
                  "unknown_error",
                  "The number of pages could not be extracted"))
          }

      } else {
        complete(
          StatusCodes.NotFound,
          ErrorResponse(
            "not_found",
            "No compiled version of paper $paperId found"))
      }

    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors and reviewers may see the number of pages"))

  }

}

