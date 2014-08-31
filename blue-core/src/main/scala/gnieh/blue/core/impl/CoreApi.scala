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
  config: Config,
  val system: ActorSystem,
  val context: BundleContext,
  val templates: Templates,
  val mailAgent: MailAgent,
  val recaptcha: ReCaptcha,
  val logger: Logger)
    extends BlueApi(couch, sessionManager, config)
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

  val route =
    post {
      pathSuffix("users") {
        // registers a new user
        registerUser
      } ~
      pathSuffix("users" / Segment / "reset") { username =>
        // performs password reset
        resetUserPassword
      } ~
      pathSuffix("papers") {
        // creates a new paper
        createPaper
      } ~
      pathSuffix("papers" / Segment / "join" / Segment) { (paperid, peerid) =>
        // join a paper
        joinPaper(paperid, peerid)
      } ~
      pathSuffix("papers" / Segment / "part" / Segment) { (paperid, peerid) =>
        // leave a paper
        partPaper(paperid, peerid)
      } ~
      pathSuffix("session") {
        // log a user in
        login
      } ~
      pathSuffix("papers" / Segment / "files" / "resources" / Segment) { (paperid, resourcename) =>
        // save a non synchronized resource
        saveResource(paperid, resourcename)
      }
    } ~
    patch {
      pathSuffix("users" / Segment / "info") { username =>
        // save the data for the authenticated user
        modifyUser(username)
      } ~
      pathSuffix("papers" / Segment / "info") { paperid =>
        // modify paper information such as paper name
        modifyPaper(paperid)
      } ~
      pathSuffix("papers" / Segment / "roles") { paperid =>
        // add or remove people involved in this paper (authors, reviewers)
        modifyRoles(paperid)
      }
    } ~
    get {
      pathSuffix("users") {
        // gets the list of users matching the given pattern
        getUsers
      } ~
      pathSuffix("users" / Segment / "info") { username =>
        // gets the data of the given user
        getUserInfo(username)
      } ~
      pathSuffix("users" / Segment / "papers") { username =>
        // gets the list of papers the given user is involved in
        getUserPapers(username)
      } ~
      pathSuffix("users" / Segment / "reset") { username =>
        // generates a password reset token
        generatePasswordReset(username)
      } ~
      pathSuffix("session") {
        // gets the currently logged in user information
        getSessionData
      } ~
      pathSuffix("papers" / Segment / "roles") { paperid =>
        // gets the list of people involved in this paper with their role
        getPaperRoles(paperid)
      } ~
      pathSuffix("papers" / Segment / "info") { paperid =>
        // gets the paper data
        getPaperInfo(paperid)
      } ~
      pathSuffix("papers" / Segment / "zip") { paperid =>
        // downloads a zip archive containing the paper files
        backupPaper("zip", paperid)
      } ~
      pathSuffix("papers" / Segment / "files" / "synchronized") { paperid =>
        // downloads the list of synchronized resources
        synchronizedResources(paperid)
      } ~
      pathSuffix("papers" / Segment / "files" / "resources") { paperid =>
        // downloads the list of non synchronized resources
        nonSynchronizedResources(paperid)
      } ~
      pathSuffix("papers" / Segment / "files" / "resources" / Segment) { (paperid, resourcename) =>
        // gets a non synchronized resource
        getResource(paperid, resourcename)
      }
    } ~
    delete {
      pathSuffix("users" / Segment) { username =>
        // unregisters the authenticated user
        deleteUser(username)
      } ~
      pathSuffix("session") {
        // log a user out
        logout
      } ~
      pathSuffix("papers" / Segment) { paperid =>
        // deletes a paper
        deletePaper(paperid)
      } ~
      pathSuffix("papers" / Segment / "files" / "resources" / Segment) { (paperid, resourcename) =>
        // deletes a non synchronized resource
        deleteResource(paperid, resourcename)
      }
    }

}
