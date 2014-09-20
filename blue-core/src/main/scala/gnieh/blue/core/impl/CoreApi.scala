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

import http._
import common._
import user._
import session._
import paper._

import com.typesafe.config.Config

import akka.actor.ActorSystem

import org.osgi.framework.BundleContext

import gnieh.sohva.async.CouchClient

import spray.routing.session.StatefulSessionManager

/** The core Api providing features to:
 *   - manage users
 *   - manage sessions
 *   - manage papers
 *
 *  @author Lucas Satabin
 */
class CoreApi(
  couch: CouchClient,
  sessionManager: StatefulSessionManager[Any],
  conf: Config,
  val system: ActorSystem,
  val context: BundleContext,
  val templates: Templates,
  val mailAgent: MailAgent,
  val recaptcha: ReCaptcha,
  val logger: Logger)
    extends BlueApi(couch, sessionManager, conf)
    with DeleteUser
    with GeneratePasswordReset
    with GetUserPapers
    with ModifyUser
    with GetUsers
    with GetUserInfo
    with Login
    with Logout
    with GetSessionData
    with RegisterUser
    with ResetUserPassword
    with CreatePaper
    with JoinPaper
    with PartPaper
    with SaveResource
    with ModifyPaper
    with ModifyRoles
    with GetPaperRoles
    with GetPaperInfo
    with BackupPaper
    with SynchronizedResources
    with NonSynchronizedResources
    with GetResource
    with DeletePaper
    with DeleteResource {

  val routes =
    pathSuffix("session") {
      post {
        // log a user in
        login
      } ~
      get {
        // gets the currently logged in user information
        getSessionData
      } ~ delete {
        // log a user out
        logout
      }
    } ~
    path("users") {
      pathEndOrSingleSlash {
        get {
          // gets the list of users matching the given pattern
          getUsers
        } ~
        post {
          // registers a new user
          registerUser
        }
      } ~
      path(Segment) { username =>
        pathEndOrSingleSlash {
          delete {
            // unregisters the authenticated user
            deleteUser(username)
          }
        } ~
        pathSuffix("info") {
          get {
            // gets the data of the given user
            getUserInfo(username)
          } ~
          patch {
            // save the data for the authenticated user
            modifyUser(username)
          }
        } ~
        pathSuffix("papers") {
          get {
            // gets the list of papers the given user is involved in
            getUserPapers(username)
          }
        } ~
        pathSuffix("reset") {
          post {
            // performs password reset
            resetUserPassword(username)
          } ~
          get {
            // generates a password reset token
            generatePasswordReset(username)
          }
        }
      }
    } ~
    path("papers") {
      pathEndOrSingleSlash {
        post {
          // creates a new paper
          createPaper
        }
      } ~
      path(Segment) { paperid =>
        pathEndOrSingleSlash {
          delete {
            // deletes a paper
            deletePaper(paperid)
          }
        } ~
        pathSuffix("info") {
          patch {
            // modify paper information such as paper name
            modifyPaper(paperid)
          } ~
          get {
            // gets the paper data
            getPaperInfo(paperid)
          }
        } ~
        pathSuffix("roles") {
          patch {
            // add or remove people involved in this paper (authors, reviewers)
            modifyRoles(paperid)
          } ~
          get {
            // gets the list of people involved in this paper with their role
            getPaperRoles(paperid)
          }
        } ~
        path("files") {
          path("resources") {
            pathEndOrSingleSlash {
              get {
                // downloads the list of non synchronized resources
                nonSynchronizedResources(paperid)
              }
            } ~
            pathSuffix(Segment) { resourcename =>
              post {
                // save a non synchronized resource
                saveResource(paperid, resourcename)
              } ~
              get {
                // gets a non synchronized resource
                getResource(paperid, resourcename)
              } ~
              delete {
                // deletes a non synchronized resource
                deleteResource(paperid, resourcename)
              }
            }
          } ~
          pathSuffix("synchronized") {
            get {
              // downloads the list of synchronized resources
              synchronizedResources(paperid)
            }
          }
        } ~
        pathSuffix("zip") {
          get {
            // downloads a zip archive containing the paper files
            backupPaper("zip", paperid)
          }
        }
        pathSuffix("join" / Segment) { peerid =>
          post {
            // join a paper
            joinPaper(paperid, peerid)
          }
        } ~
        pathSuffix("part" / Segment) { peerid =>
          post {
            // leave a paper
            partPaper(paperid, peerid)
          }
        }
      }
    }

}
