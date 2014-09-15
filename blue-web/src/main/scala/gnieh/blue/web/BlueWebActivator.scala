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

import org.osgi.framework._
import org.osgi.service.log.LogService

import com.typesafe.config.Config

import gnieh.sohva.async.CouchClient

import common.{
  ConfigurationLoader,
  OsgiUtils,
  Logger
}

import spray.routing.session.StatefulSessionManager

/** The `BlueWebActivator` registers the HLet that serves the blue web client
 *
 *  @author Lucas Satabin
 */
class BlueWebActivator extends BundleActivator {

  import OsgiUtils._

  def start(context: BundleContext): Unit =
    for {
      loader <- context.get[ConfigurationLoader]
      logger <- context.get[Logger]
      couch <- context.get[CouchClient]
      sessionManager <- context.get[StatefulSessionManager[Any]]
    } try {
      val config = loader.load(context.getBundle)
      // register the web application
      context.registerService(classOf[BlueApi], new WebApp(couch, sessionManager, config, context, logger), null)
    } catch {
      case e: Exception =>
        logger.log(LogService.LOG_ERROR, s"Unable to start the web client bundle", e)
        throw e
    }

  def stop(context: BundleContext): Unit = {
  }

}


