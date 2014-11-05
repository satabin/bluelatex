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

import java.io.File

import org.osgi.framework._
import org.osgi.service.log.LogService

import com.typesafe.config._

import akka.actor.ActorSystem

import spray.routing.session.StatefulSessionManager

import http.BlueApi

import common._

import gnieh.sohva.async.CouchClient

/** The `BlueActivator` starts the \BlueLaTeX core system:
 *   - the configuration loader
 *   - the actor system
 *
 *  @author Lucas Satabin
 */
class BlueCoreActivator extends BundleActivator {

  import OsgiUtils._

  def start(context: BundleContext): Unit =
    for {
      loader <- context.get[ConfigurationLoader]
      logger <- context.get[Logger]
      system <- context.get[ActorSystem]
      sessionManager <- context.get[StatefulSessionManager[Any]]
      couch <- context.get[CouchClient]
      templates <- context.get[Templates]
      mailAgent <- context.get[MailAgent]
      recaptcha <- context.get[ReCaptcha]
    } try {
      // load the \BlueLaTeX common configuration
      val config = loader.load(context.getBundle)
      val configuration = new BlueConfiguration(config)

      // register the core Rest API
      context.registerService(classOf[BlueApi], new CoreApi(couch, sessionManager, config, system, context, templates, mailAgent, recaptcha, logger), null)
    } catch {
      case e: Exception =>
        logger.log(LogService.LOG_ERROR, s"Unable to start the core bundle", e)
        throw e
    }

  def stop(context: BundleContext): Unit = {
  }

}

