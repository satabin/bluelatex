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

import http._
import common._

import com.typesafe.config.Config

import org.osgi.framework.BundleContext

import gnieh.sohva.async.CouchClient

import spray.routing.session.StatefulSessionManager

/** The compilation feature Rest API that offers the services to compile
 *  papers
 *
 *  @author Lucas Satabin
 */
class CompilationApi(
  couch: CouchClient,
  sessionManager: StatefulSessionManager[Any],
  conf: Config,
  val dispatcher: ActorRef,
  val context: BundleContext,
  val logger: Logger)
    extends BlueApi(couch, sessionManager, conf)
    with Compile
    with ModifyCompiler
    with GetCompilers
    with GetPdf
    with GetLog
    with GetPng
    with GetPages
    with GetCompilerSettings
    with GetSyncTeX
    with Cleanup {

  val routes =
    post {
      // join the paper compiler stream
      pathSuffix("papers" / Segment / "compiler") { paperid =>
        compile(paperid)
      }
    } ~
    patch {
      // saves the compilation settings
      pathSuffix("papers" / Segment / "compiler") { paperid =>
        modifyCompiler(paperid)
      }
    } ~
    get {
      // return the list of available compilers
      pathSuffix("compilers") {
        getCompilers
      } ~
      // return the compiled pdf file for the paper, if any
      pathSuffix("papers" / Segment / "compiled" / "pdf") { paperid =>
        getPdf(paperid)
      } ~
      // return the last compilation log of any
      pathSuffix("papers" / Segment / "compiled" / "log") { paperid =>
        getLog(paperid)
      } ~
      // return the page given as parameter converted as a png image
      pathSuffix("papers" / Segment / "compiled" / "png") { paperid =>
        getPng(paperid)
      } ~
      // returns the number of pages in the compiled paper
      pathSuffix("papers" / Segment / "compiled" / "pages") { paperid =>
        getPages(paperid)
      } ~
      // return the compilation settings
      pathSuffix("papers" / Segment / "compiler") { paperid =>
        getCompilerSettings(paperid)
      } ~
      // return the SyncTeX file
      pathSuffix("papers" / Segment / "synctex") { paperid =>
        getSyncTeX(paperid)
      }
    } ~
    delete {
      // cleanup the working directory
      pathSuffix("papers" / Segment / "compiled") { paperid =>
        cleanup(paperid)
      }
    }

}
