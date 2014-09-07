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

import common._

import com.typesafe.config.Config

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import net.liftweb.json._
import net.liftweb.json.Serialization

import scala.concurrent.{
  Future,
  Promise
}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import java.util.Date

/** A synchronization server.
 *
 * This synchronization server makes use of the mobwrite-based
 * Synchronization Protocol Scala implementation.
 *
 *  @author Audric Schiltknecht
 *  @author Lucas Satabin
 */
class SyncServer(dispatcher: ActorRef, configuration: Config) extends SynchroServer {

  private implicit val timeout = Timeout(2.seconds)

  protected[impl] implicit val formats =
    DefaultFormats +
    new SyncSessionSerializer +
    new SyncCommandSerializer +
    new SyncActionSerializer +
    new EditSerializer

  def session(data: String): Future[String] = {
    val syncSession = Serialization.read[SyncSession](data)
    for(response <- (dispatcher ? Forward(syncSession.paperId, syncSession)).mapTo[SyncSession])
      yield Serialization.write[SyncSession](response)
  }

  def persist(paperId: String): Future[Unit] = {
    val promise = Promise[Unit]()

    dispatcher ! Forward(paperId, PersistPaper(promise))
    promise.future
  }

  def lastModificationDate(paperId: String): Future[Date] = {
    val promise = Promise[Date]()

    dispatcher ! Forward(paperId, LastModificationDate(promise))
    promise.future
  }

  def shutdown(): Unit = {
    dispatcher ! Stop
  }
}
