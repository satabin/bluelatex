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

import spray.routing.Route

/** Returns the base configuration data needed by the client.
 *
 *  @author Lucas Satabin
 */
trait Configuration {
  this: WebApp =>

  def configuration: Route = {
    val recaptcha =
      if(config.hasPath("recaptcha.public-key")) Some(config.getString("recaptcha.public-key"))
      else None
    val compilationType =
      if(config.hasPath("compiler.compilation-type")) Some(config.getString("compiler.compilation-type"))
      else None

    val issuesURL = config.getString("blue.client.issues-url") match {
      case "" => None
      case s  => Some(s)
    }

    val cloneURL = config.getString("blue.client.clone-url") match {
      case "" => None
      case s  => Some(s)
    }

    complete(
      AppConfig(
        config.getString("blue.api.path-prefix"),
        recaptcha,
        compilationType,
        issuesURL,
        compilationType))
   }

}
