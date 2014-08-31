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

import java.util.zip.{
  ZipOutputStream,
  ZipEntry,
  ZipException
}
import java.io.{
  FileInputStream,
  ByteArrayOutputStream
}

import resource._

import http._
import common._
import permission._

import couch.Paper


import spray.routing.Route

import spray.http.{
  MediaTypes,
  StatusCodes
}

import spray.httpx.marshalling.BasicMarshallers

import scala.util.{
  Success,
  Failure
}

/** Backup the paper sources as a zip file
 *
 *  @author Lucas Satabin
 */
trait BackupPaper {
  this: CoreApi =>

  import FileUtils._

  def backupPaper(format: String, paperId: String): Route = (withRole(paperId) & withEntityManager("blue_papers")) { (role, entityManager) =>
    role match {
      case Author =>
        onSuccess {
          entityManager.getComponent[Paper](paperId) map {
            case Some(Paper(_, name, _)) =>
              // only authors may backup the paper sources
              import FileUtils._
              val toZip =
                paperConfig.paperDir(paperId).filter(f => !f.isDirectory && !f.isHidden && !f.isTeXTemporary).toArray

              val array = (for {
                os <- managed(new ByteArrayOutputStream)
                zip <- managed(new ZipOutputStream(os))
              } yield {
                for (file <- toZip) {
                  for(fis <- managed(new FileInputStream(file))) {
                    try {
                      // create a new entry
                      zip.putNextEntry(new ZipEntry(file.getName))
                      // write into this entry
                      val length = fis.available
                      for (i <- 0 until length)
                        zip.write(fis.read)
                      // close the entry which was just written
                      zip.closeEntry
                    } catch {
                      case e: ZipException =>
                    }
                  }
                }

                zip.finish

                os.toByteArray
              }).acquireAndGet(identity)

              (name, array)

            case None =>
              throw new BlueHttpException(StatusCodes.NotFound, "not_found", s"No paper data for paper $paperId")

          }

        } {
          case (name, response) =>
            respondWithMediaType(MediaTypes.`application/zip`) {
              respondWithFileName(s"$name.$format") {
                complete(response)
              }
            }

        }

      case _ =>
        throw new BlueHttpException(StatusCodes.Forbidden, "no_sufficient_rights", "Only authors may backup the paper sources")
    }
  }

}
