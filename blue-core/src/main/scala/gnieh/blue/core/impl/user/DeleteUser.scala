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
package user

import gnieh.sohva.Row

import http._
import couch._
import common._

import com.typesafe.config.Config

import org.osgi.framework.BundleContext

import gnieh.diffson._

import scala.util.{
  Try,
  Success
}

import gnieh.sohva.control.CouchClient

import net.liftweb.json.JBool

import spray.routing.Route

import spray.http.StatusCodes

/** handles unregistration of a user.
 *  When a user unregisters, if there are papers for which he is the single author,
 *  the user cannot be unregistered and the process is aborted.
 *
 *  @author Lucas Satabin
 */
trait DeleteUser {
  this: CoreApi =>

  def deleteUser(username: String): Route = requireUser { user =>
    authorize(recaptcha) {
      if(user.name == username) {

        val userid = s"org.couchdb.user:$username"

        withView("blue_papers", "papers", "for") { view =>
          // get the papers in which this user is involved
          onSuccess(view.query[String, UserRole, PaperRole](key = Some(username), include_docs = true)) { res =>
            val rows = res.rows

            (withEntityManager("blue_users") & withCouch) { (userManager, couchSession) =>

              // get the papers for which the user is the single author
              val singleAuthor = rows.collect {
                case Row(_, _, _, Some(roles @ SingleAuthor(name))) if name == username =>
                  roles._id
              }

              if(singleAuthor.isEmpty) {
                // ok no paper for which this user is the single author, let's remove him
                // first delete the \BlueLaTeX specific user document
                onSuccess(userManager.deleteEntity(userid)) {
                  case true =>
                    // delete the couchdb user
                    couchSession.users.delete(username)
                    // remove user name from papers it is involved in
                    // get all papers in which the user is involved and remove his name
                    val newPapers =
                      for(Row(_, _, _, Some(p)) <- rows)
                        yield p.copy(authors = p.authors - user, reviewers = p.reviewers - user).withRev(p._rev)
                    onSuccess(couchSession.database(blue_papers).saveDocs(newPapers)) { _ =>
                      import OsgiUtils._
                      // notifiy deletion hooks
                      for(hook <- context.getAll[UserUnregistered])
                        Try(hook.afterUnregister(userid, userManager))

                      complete(JBool(true))
                    }
                  case false =>
                    logError(s"Cannot delete \\BlueLaTeX user $userid")
                    complete(
                      StatusCodes.InternalServerError,
                      ErrorResponse(
                        "cannot_unregister",
                        "Unable to delete user data"))
                }

              } else {
                // Nope! You must first transfer the papers ownership, or delete them!
                complete(
                  StatusCodes.Forbidden,
                  ErrorResponse(
                    "cannot_unregister",
                    s"""Your are the single author of the following papers: ${singleAuthor.mkString("[", ", ", "]")}"""))
              }
            }
          }
        }

      } else {
        complete(
          StatusCodes.Unauthorized,
          ErrorResponse(
            "not_authorized",
            "ReCaptcha did not verify"))
      }

    }
  }
}
