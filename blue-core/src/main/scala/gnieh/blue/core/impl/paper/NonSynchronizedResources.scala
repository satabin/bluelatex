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

import http._
import common._
import permission._

import scala.io.Source

import scala.util.Try

import gnieh.sohva.control.CouchClient

import spray.http.StatusCodes

import spray.routing.Route

/** Gives access to the non-synchronized resource list for the given paper.
 *
 *  @author Lucas Satabin
 */
trait NonSynchronizedResources {
  this: CoreApi =>

  def nonSynchronizedResources(paperId: String): Route = withRole(paperId) {
    case Author =>
      // only authors may get the list of synchronized resources
      import FileUtils._
      val files =
        paperConfig
          .paperDir(paperId)
          .filter(f =>
              !f.extension.matches(synchronizedExt)
              && !f.extension.matches(generatedExt)
              && !f.isHidden
              && !f.isDirectory
          )
          .map(_.getName)
      complete(files)
    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors may see the list of non synchronized resources"))
  }

}
