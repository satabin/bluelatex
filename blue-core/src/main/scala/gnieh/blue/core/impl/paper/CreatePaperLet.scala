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

import http._
import couch._
import common._

import java.util.UUID
import java.io.{
  File,
  FileWriter
}

import tiscaf._

import com.typesafe.config.Config

import org.osgi.framework.BundleContext

import resource._

import scala.sys.process._

import scala.util.{
  Try,
  Success
}

import gnieh.sohva.control.CouchClient

/** Create a new paper. The currently authenticated user is added as author of this paper
 *
 *  @author Lucas Satabin
 */
class CreatePaperLet(val couch: CouchClient, config: Config, context: BundleContext, templates: Templates, logger: Logger) extends SyncBlueLet(config, logger) with SyncAuthenticatedLet {

  def authenticatedAct(user: UserInfo)(implicit talk: HTalk): Try[Any] =
    talk.req.param("paper_title") match {
      case Some(title) =>
        // new paper identifier
        val newId = "w" + UUID.randomUUID.getMostSignificantBits.toHexString

        val template = talk.req.param("template").getOrElse("article")

        val configuration = new PaperConfiguration(config)

        if(configuration.paperDir(newId).mkdirs) {

          // if the template is not one of the standard styles,
          // then there should be an associated .sty file to be copied in `resources'
          // directory
          template match {
            case "article" | "book" | "report" =>
              // built-in template, ignore it
            case cls if configuration.cls(cls).exists =>
              // copy the file to the working directory
              logDebug(s"Copying class ${configuration.cls(cls)} to paper directory ${configuration.paperDir(newId)}")
              (configuration.cls(cls) #> new File(configuration.paperDir(newId), cls + ".cls")) !
                CreationProcessLogger
            case cls =>
              // just log that the template was not found. It can however be uploaded later by the user
              logDebug(s"Class $cls was not found, the user will have to upload it later")
          }

          // write the template to the newly created paper
          for(fw <- managed(new FileWriter(configuration.paperFile(newId)))) {
            fw.write(templates.layout(s"$template.tex", "title" -> title, "id" -> newId))
          }

          // create empty bibfile
          configuration.bibFile(newId).createNewFile

          import OsgiUtils._

          // create the paper database
          for(_ <- database("blue_papers").saveDoc(Paper(newId, title, Set(user.name), Set(), template)))
            yield {
              // notifiy creation hooks
              for(hook <- context.getAll[PaperCreated])
                Try(hook.afterCreate(newId, couchSession))
              talk.setStatus(HStatus.Created).writeJson(newId)
            }
        } else {
          logError(s"Unable to create the paper directory: ${configuration.paperDir(newId)}")
          Try(talk
            .setStatus(HStatus.InternalServerError)
            .writeJson(ErrorResponse("cannot_create_paper", "Something went wrong on the server side")))
        }

      case None =>
        // missing parameter
        Success(
          talk
            .setStatus(HStatus.BadRequest)
            .writeJson(ErrorResponse("cannot_create_paper", "Some parameters are missing")))

    }

  object CreationProcessLogger extends ProcessLogger {
    def out(s: => String) =
      logInfo(s)
    def err(s: => String) =
      logError(s)
    def buffer[T](f: => T) = f
  }

}
