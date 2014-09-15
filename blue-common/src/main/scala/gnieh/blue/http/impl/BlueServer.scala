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
package impl

import org.osgi.framework.BundleContext

import com.typesafe.config._

import scala.collection.mutable.{
  ListBuffer
}

import scala.concurrent.duration._
import scala.concurrent.SyncVar

import common._

import akka.actor.{
  Actor,
  ActorSystem,
  Props
}
import akka.io.IO
import akka.util._

import spray.can.Http
import spray.routing.{
  Route,
  HttpServiceActor
}
import spray.routing.session.{
  StatefulSessionManager,
  InMemorySessionManager
}
import spray.http.StatusCodes

class BlueServer(context: BundleContext, configuration: Config, val logger: Logger)(implicit system: ActorSystem) extends Logging {

  private implicit val timeout = Timeout(10.seconds)

  val port =
    configuration.getInt("http.port")

  val host =
    configuration.getString("http.host")

  private var started =
    new SyncVar[Boolean]
  started.put(false)

  private val serverActor =
    system.actorOf(Props(new ServerActor(configuration, logger)))

  private var sessionManager: Option[StatefulSessionManager[Any]] =
    None

  def start(): Unit = started.synchronized {
    if(!started.get) {
      started.take
      val man = new InMemorySessionManager[Any](configuration)
      // register the session manager service
      context.registerService(classOf[StatefulSessionManager[Any]], man, null)
      sessionManager = Some(man)
      IO(Http) ! Http.Bind(serverActor, host, port)
      started.put(true)
      logInfo("blue server started")
    }
  }

  def stop(): Unit = started.synchronized {
    if(started.get) {
      started.take
      sessionManager.foreach(_.shutdown)
      sessionManager = None
      IO(Http) ! Http.Unbind(Duration.Inf)
      // stop the application tracker
      serviceTracker.close()
      started.put(false)
    }
  }

  import OsgiUtils._

  private val serviceTracker =
    context.trackAll[BlueApi] {
      case ServiceAdded(api, id) =>
        // transfer the event to the actor so that it can act accordingly
        serverActor ! RouteAdded(api.route, id)
      case ServiceRemoved(_, id) =>
        serverActor ! RouteRemoved(id)
    }

}

private class ServerActor(config: Config, val logger: Logger) extends HttpServiceActor with Logging {

  def receive = running(Map())

  private def running(routes: Map[Long,Route]): Receive =
    runRoute(mkRoute(routes)) orElse {
      case RouteAdded(route, id) =>

        logInfo("Route added")
        // a new Rest API is registered
        context.become(running(routes.updated(id, route)))

      case RouteRemoved(id) =>

        logInfo("Route removed")
        // a bundle disappeared, remove associated route
        context.become(running(routes - id))
    }

  private def mkRoute(routes: Map[Long,Route]): Route =
    if(routes.isEmpty)
      reject()
    else
      routes.values.reduce(_ ~ _)

}

case class RouteAdded(route: Route, id: Long)
case class RouteRemoved(id: Long)
