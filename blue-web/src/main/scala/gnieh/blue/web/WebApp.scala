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
package web

import http.BlueApi

import common.Logger

import org.osgi.framework.BundleContext

import com.typesafe.config.Config

import gnieh.sohva.async.CouchClient

import spray.routing.session.StatefulSessionManager

import spray.routing.Route

/** This web application serves static web client
 *
 *  @author Lucas Satabin
 */
class WebApp(
  couch: CouchClient,
  sessionManager: StatefulSessionManager[Any],
  conf: Config,
  val context: BundleContext,
  val logger: Logger)
    extends BlueApi(couch, sessionManager, conf)
    with Configuration
    with WebClient {

  override val withApiPrefix = false

  private lazy val prefix = {
    val confPrefix = config.getString("blue.client.path-prefix")
    if(confPrefix.isEmpty)
      pass
    else
      pathPrefix(separateOnSlashes(confPrefix))
  }

  def routes: Route =
    prefix {
      path("configuration") {
        configuration
      }
    } ~
    webClient

}
