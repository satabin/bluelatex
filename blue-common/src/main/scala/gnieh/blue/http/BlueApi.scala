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
  Anonymous
}

import spray.routing.{
  Route,
  Directives,
  Directive,
  Directive1,
  Directive0,
  ExceptionHandler,
  Rejection,
  MethodRejection,
  RejectionHandler,
  RequestEntityExpectedRejection
}
import spray.routing.session.{
  StatefulSessionManager,
  InvalidSessionRejection
}
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

  def isMethodRejection(rejection: Rejection): Boolean = rejection match {
    case MethodRejection(_) => true
    case _                  => false
  }

  def isAuthenticationRequired(rejections: List[Rejection]): Boolean =
    rejections.exists {
      case InvalidSessionRejection(_) | UserRequiredRejection => true
      case _ => false
    }

  def isContentRequired(rejections: List[Rejection]): Boolean =
    rejections.exists {
      case RequestEntityExpectedRejection => true
      case _ => false
    }

  val exceptionHandler = ExceptionHandler {
    case BlueHttpException(status, key, message) =>
      complete(status, ErrorResponse(key, message))
    case t =>
      complete(StatusCodes.InternalServerError,
        ErrorResponse(
          "unknown_error",
          "Something wrong happened on the server side. If the problem persists please contact an administrator"))
  }

  val rejectionHandler = RejectionHandler {
    case rejections if isAuthenticationRequired(rejections) =>
      complete(StatusCodes.Unauthorized, ErrorResponse("not_allowed", "Authenticated user required"))
    case rejections if isContentRequired(rejections) =>
      complete(StatusCodes.NotModified, ErrorResponse("nothing_to_do" ,"No content was sent"))
  }

  val route =
    if(withApiPrefix)
      prefix {
        handleExceptions(exceptionHandler) {
          handleRejections(rejectionHandler) {
            withCookieSession() { (_, _) =>
              routes
            }
          }
        }
      }
    else
      handleExceptions(exceptionHandler) {
        withCookieSession() { (_, _) =>
          routes
        }
      }

  def withCouch: Directive1[CookieSession] = cookieSession() hmap {
    case id :: map :: HNil =>
      map.get(SessionKeys.Couch).collect {
        case sess: CookieSession => sess
      } getOrElse {
        // if no couch session is registered, then start a new one
        val sess = couch.startCookieSession
        // and save it
        updateSession(id, map.updated(SessionKeys.Couch, sess))
        // then return it
        sess
      }
  }

  def withUser: Directive1[Option[UserInfo]] = withCouch flatMap { session =>
    onSuccess(session.currentUser)
  }

  def requireUser: Directive1[UserInfo] = withUser flatMap {
    case Some(user) => provide(user)
    case None       => reject(UserRequiredRejection)
  }

  def withDatabase(dbName: String): Directive1[Database] = withCouch map { session =>
    session.database(couchConfig.database(dbName))
  }

  def withView(dbName: String, design: String, view: String): Directive1[View] = withCouch map { session =>
    session.database(couchConfig.database(dbName)).design(design).view(view)
  }

  def withEntityManager(dbName: String): Directive1[EntityManager] = withCouch map { session =>
    new EntityManager(session.database(couchConfig.database(dbName)))
  }

  def withSession: Directive[String :: Map[String, Any] :: HNil] = cookieSession()

  def invalidate: Directive0 = withSession hflatMap { case id :: _ :: HNil =>
    invalidateSession(id)
  }

  def withRole(paperId: String): Directive1[Role] = (withUser & withEntityManager("blue_papers")) hflatMap {
    case Some(user) :: entityManager :: HNil =>
      onSuccess(for(Some(roles) <- entityManager.getComponent[PaperRole](paperId))
        yield roles.roleOf(Some(user)))
    case None :: _ :: HNil =>
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
