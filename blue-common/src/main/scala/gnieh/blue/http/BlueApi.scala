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
package http

import common.{
  CouchConfiguration,
  PaperConfiguration,
  UserInfo,
  Logging
}
import couch.PaperRole
import permission.{
  Role,
  Anonymous,
  Other
}

import spray.routing.{
  Route,
  Rejection,
  Directives,
  Directive,
  Directive1,
  Directive0
}
import spray.routing.session.StatefulSessionManager
import spray.routing.session.directives.StatefulSessionManagerDirectives

import spray.http.{
  HttpHeaders,
  HttpRequest,
  StatusCodes,
  StatusCode
}
import spray.httpx.LiftJsonSupport

import com.typesafe.config.Config

import gnieh.sohva.async.{
  CouchClient,
  CookieSession,
  Database,
  View
}
import gnieh.sohva.async.entities.EntityManager

import gnieh.diffson.JsonPatchSerializer

import shapeless._

import java.net.URLEncoder

import scala.util.{
  Success,
  Failure
}

import scala.concurrent.Future

/** The rest interface may be extended by \BlueLaTeX modules.
 *  Theses module simply need to register routes to make the new interface available.
 *
 *  '''Note''': make sure that the route in your module does not collide
 *  with another existing and already registered module. In such a case, the first
 *  service found in the list will be taken, which order is not possible to predict
 *
 *  @author Lucas Satabin
 */
abstract class BlueApi(
  val couch: CouchClient,
  val sessionManager: StatefulSessionManager[Any],
  val config: Config)
    extends Directives
    with Logging
    with StatefulSessionManagerDirectives[Any]
    with LiftJsonSupport {

  implicit def liftJsonFormats = couch.formats + JsonPatchSerializer

  implicit val manager = sessionManager

  implicit val executor = couch.ec

  val couchConfig =
    new CouchConfiguration(config)

  val paperConfig =
    new PaperConfiguration(config)

  /** The configured API prefix if any */
  private val prefix =
    pathPrefix(separateOnSlashes(config.getString("blue.api.path-prefix")))

  /** Override this to avoid api prefix to be automatically added to the route */
  val withApiPrefix = true

  val route =
    if(withApiPrefix)
      prefix(routes)
    else
      routes

  def withCouch(): Directive1[CookieSession] = cookieSession() hflatMap {
    case id :: map :: HNil =>
      val sess = map.get(SessionKeys.Couch).collect {
        case sess: CookieSession =>
          Future.successful(sess)
      } getOrElse {
        // if no couch session is registered, then start a new one
        val sess = couch.startCookieSession
        val updated = map.updated(SessionKeys.Couch, sess)
        // and save it
        manager.update(id, updated) map { _ =>
          sess
        }
      }
      onSuccess(sess)
  }

  def withUser(): Directive1[Option[UserInfo]] = withCouch() flatMap { session =>
    onSuccess(session.currentUser)
  }

  def requireUser: Directive1[UserInfo] = withUser() map {
    case Some(user) => user
    case None       => throw new BlueHttpException(StatusCodes.Unauthorized, "unauthorized", "Authenticated user is required")
  }

  def withDatabase(dbName: String): Directive1[Database] = withCouch() map { session =>
    session.database(couchConfig.database(dbName))
  }

  def withView(dbName: String, design: String, view: String): Directive1[View] = withCouch() map { session =>
    session.database(couchConfig.database(dbName)).design(design).view(view)
  }

  def withEntityManager(dbName: String): Directive1[EntityManager] = withCouch() map { session =>
    new EntityManager(session.database(couchConfig.database(dbName)))
  }

  def withSession: Directive[String :: Map[String, Any] :: HNil] = cookieSession()

  def invalidate: Directive0 = withSession hflatMap { case id :: map :: HNil =>
    map.get(SessionKeys.Couch).collect { case sess: CookieSession => sess.logout }
    invalidateSession(id)
  }

  def withRole(paperId: String): Directive1[Role] = withUser() flatMap {
    case Some(user) =>
      withEntityManager("blue_papers") flatMap { entityManager =>
        onSuccess {
          entityManager.getComponent[PaperRole](paperId) map {
            case Some(roles) => roles.roleOf(Some(user))
            case None        => Other
          }
        }
      }
    case None =>
      provide(Anonymous)
  }

  val blue_users =
    dbname("blue_users")

  val blue_papers =
    dbname("blue_papers")

  def dbname(name: String): String =
    couchConfig.database(name)

  def respondWithFileName(name: String): Directive0 = {
    val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    respondWithHeader(HttpHeaders.`Content-Disposition`("attachment", Map("filename" -> encoded, "filename*" -> s"utf-8''$encoded")))
  }

  def rejectWithStatus(status: StatusCode, key: String, message: String): Route =
    respondWithStatus(status) {
      complete(ErrorResponse(key, message))
    }

  def routes: Route

}

case object UserRequiredRejection extends Rejection
