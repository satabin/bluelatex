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
package compile
package impl

import akka.actor.ActorRef
import akka.pattern.ask

import http._
import common._
import permission._

import scala.concurrent._

import scala.util.{
  Success,
  Failure
}

import gnieh.sohva.async.CouchClient

import spray.routing.Route

import spray.http.StatusCodes

import net.liftweb.json.JBool

trait Compile {
  this: CompilationApi =>

  def compile(paperId: String): Route = withRole(paperId) {
    case Author =>
      val promise = Promise[CompilationStatus]()

      requireUser { user =>
        onComplete {
          // register the client with the paper compiler
          dispatcher ! Forward(paperId, Register(user.name, promise))
          promise.future
        } {
          case Success(CompilationSucceeded) =>
            complete(JBool(true))
          case Success(CompilationFailed) =>
            complete(
              StatusCodes.InternalServerError,
              ErrorResponse(
                "unable_to_compile",
                "Compilation failed, more details in the compilation log file."))
          case Success(CompilationAborted) =>
            complete(
              StatusCodes.ServiceUnavailable,
              ErrorResponse(
                "unable_to_compile",
                s"No compilation task started"))
          case Success(CompilationUnnecessary) =>
            complete(
              StatusCodes.NotModified,
              JBool(false))
          case Failure(e) =>
            logError(s"Unable to compile paper $paperId", e)
            complete(
              StatusCodes.InternalServerError,
              ErrorResponse(
                "unable_to_compile",
                "Compilation failed, more details in the compilation log file."))
        }
      }

    case _ =>
      complete(
        StatusCodes.Forbidden,
        ErrorResponse(
          "no_sufficient_rights",
          "Only authors may compile a paper"))

  }

}

