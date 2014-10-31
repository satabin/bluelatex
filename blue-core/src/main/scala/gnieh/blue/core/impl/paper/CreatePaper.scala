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

import java.util.{
  Date,
  UUID
}
import java.io.{
  File,
  FileWriter
}

import com.typesafe.config.Config

import org.osgi.framework.BundleContext

import resource._

import scala.sys.process._

import scala.util.{
  Try,
  Success
}

import gnieh.sohva.control.CouchClient

import spray.routing.Route

import spray.http.StatusCodes

import spray.httpx.unmarshalling.FormDataUnmarshallers

/** Create a new paper. The currently authenticated user is added as author of this paper
 *
 *  @author Lucas Satabin
 */
trait CreatePaper {
  this: CoreApi =>

  import FormDataUnmarshallers._

  def createPaper: Route =
    formFields("paper_name".?, "paper_title".?, "template" ? "article", "type" ? "latex") { (name, title, template, tpe) =>
      (withEntityManager("blue_papers") & withEntityManager("blue_users") ){ (paperManager, userManager) =>
        requireUser() { user =>
            (name, title) match {
              case (Some(name), Some(title)) =>

                val newId = s"x${UUID.randomUUID.getMostSignificantBits.toHexString}"

                onSuccess {
                  for {
                    // create the paper into the database
                    () <- paperManager.create(newId, None)
                    // add the core component which contains the type, the title
                    paper <- paperManager.saveComponent(newId, Paper(s"$newId:core", name, new Date))
                    // add the permissions component to set the creator as author
                    roles <- paperManager.saveComponent(newId, PaperRole(s"$newId:roles", UsersGroups(Set(user.name), Set()), UsersGroups(Set(), Set()),
                      UsersGroups(Set(), Set())))
                    user <- userManager.getComponent[User](s"org.couchdb.user:${user.name}")
                  } yield {
                    if(paperConfig.paperDir(newId).mkdirs) {

                      // if the template is not one of the standard styles,
                      // then there should be an associated .sty file to be copied in `resources'
                      // directory
                      val templateName = template match {
                        case "article" | "book" | "report" =>
                          // built-in template, ignore it
                          "generic"
                        case "beamer" =>
                          "beamer"
                        case cls if paperConfig.cls(cls).exists =>
                          // copy the file to the working directory
                          logDebug(s"Copying class ${paperConfig.cls(cls)} to paper directory ${paperConfig.paperDir(newId)}")
                          (paperConfig.cls(cls) #> new File(paperConfig.paperDir(newId), cls + ".cls")) !
                            CreationProcessLogger
                          cls
                        case cls =>
                          // just log that the template was not found. It can however be uploaded later by the user
                          logDebug(s"Class $cls was not found, the user will have to upload it later")
                          "generic"
                      }

                      // write the template to the newly created paper
                      for(fw <- managed(new FileWriter(paperConfig.paperFile(newId)))) {
                        fw.write(
                          templates.layout(
                            s"$templateName.tex",
                            "class" -> template,
                            "title" -> title,
                            "id" -> newId,
                            "author" -> user.map(_.fullName).getOrElse("Your Name"),
                            "email" -> user.map(_.email).getOrElse("your@email.com"),
                            "affiliation" -> user.flatMap(_.affiliation).getOrElse("Institute")
                          )
                        )
                      }

                      // create empty bibfile
                      paperConfig.bibFile(newId).createNewFile

                      import OsgiUtils._

                      // notifiy creation hooks
                      for(hook <- context.getAll[PaperCreated])
                        Try(hook.afterCreate(newId, paperManager)) recover {
                          case e => logError("Error in post paper creation hook", e)
                        }

                      newId

                    } else {
                      logError(s"Unable to create the paper directory: ${paperConfig.paperDir(newId)}")
                      throw new BlueHttpException(
                        StatusCodes.InternalServerError,
                        "cannot_create_paper",
                        "Something went wrong on the server side")
                    }
                  }
                } { id =>
                  complete(StatusCodes.Created, id)
                }

              case (_, _) =>
                // missing parameter
                throw new BlueHttpException(
                  StatusCodes.BadRequest,
                  "cannot_create_paper",
                  "Some parameters are missing")
            }
        }
      }
    }

  object CreationProcessLogger extends ProcessLogger {
    def out(s: => String) =
      logInfo(s)
    def err(s: => String) =
      logError(s)
    def buffer[T](f: => T) = f
  }

}
