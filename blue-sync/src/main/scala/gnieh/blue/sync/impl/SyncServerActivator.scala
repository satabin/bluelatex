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
package sync
package impl

import org.osgi.framework._
import org.osgi.service.log.LogService

import akka.actor._

import common._
import http._

import gnieh.sohva.async.CouchClient

import spray.routing.session.StatefulSessionManager

/** Registers the synchro service
 *
 *  @author Lucas Satabin
 */
class SyncServerActivator extends BundleActivator {

  import OsgiUtils._

  private var _server: Option[SyncServer] = None

  def start(context: BundleContext): Unit =
    for {
      loader <- context.get[ConfigurationLoader]
      system <- context.get[ActorSystem]
      couch <- context.get[CouchClient]
      logger <- context.get[LogService]
      sessionManager <- context.get[StatefulSessionManager[Any]]
    } try {
      val config = loader.load(context.getBundle)
      // create the dispatcher actor
      val dispatcher = system.actorOf(Props(new SyncDispatcher(context, config, logger)), name = "sync-dispatcher")
      // instantiate the sync server as synchronization server
      val server = new SyncServer(dispatcher, config)
      // register this as the synchronization server
      context.registerService(classOf[SynchroServer], server, null)
      _server = Some(server)

      //register the Rest API
      context.registerService(classOf[BlueApi], new SyncApi(couch, sessionManager, config, server, logger), null)
    } catch {
      case e: Exception =>
        logger.log(LogService.LOG_ERROR, s"Unable to start the synchronization bundle", e)
        throw e
    }

  def stop(context: BundleContext): Unit =
    for (server <- _server)
      server.shutdown()

}
