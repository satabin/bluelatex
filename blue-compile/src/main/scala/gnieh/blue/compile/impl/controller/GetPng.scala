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

// image generation
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.util.PDFImageWriter
import java.awt.image.BufferedImage

import java.io.FileInputStream

import resource._

import spray.routing.Route

import spray.http.{
  StatusCodes,
  MediaTypes
}

import spray.httpx.marshalling.BasicMarshallers

trait GetPng {
  this: CompilationApi =>

  import FileUtils._

  def getPng(paperId: String): Route = withRole(paperId) {
    case Author | Reviewer =>
      parameter('page.as[Int].?(1)) { page =>
        val pngPage = paperId + "-" + page + ".png"
        val pngFile = paperConfig.buildDir(paperId) / pngPage

        if (!pngFile.exists) {

          // the generated pdf file
          val paperDir = paperConfig.buildDir(paperId)
          val pdfFile = paperDir / s"main.pdf"

          if (pdfFile.exists) {

            // if the pdf file was generated (or at least one was generated last time...)
            for(doc <- managed(PDDocument.load(pdfFile))) {
              val imageWriter = new PDFImageWriter
              imageWriter.writeImage(doc, "png", null, page, page,
                (paperConfig.buildDir(paperId) / paperId).getCanonicalPath + "-",
                BufferedImage.TYPE_INT_RGB, 100)
            }
          }
        }

        if (pngFile.exists) {
          val array = managed(new FileInputStream(pngFile)) acquireAndGet { is =>
            Iterator.continually(is.read).takeWhile(_ != -1). map(_.toByte).toArray
          }

          respondWithMediaType(MediaTypes.`image/png`) {
            import BasicMarshallers._
            complete(array)
          }
        } else {
          complete(
            StatusCodes.NotFound,
            ErrorResponse(
              "not_found",
              "page $page not found for paper $paperId"))
        }
      }

    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors and reviewers may see compiled paper"))

  }

}

