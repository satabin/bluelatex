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

import org.osgi.framework.BundleContext

import java.io.{
  File,
  PushbackInputStream,
  InputStream
}

import common.FileUtils

import spray.routing.Route

import spray.http.MediaTypes

import spray.httpx.marshalling.BasicMarshallers

import scalax.io.Resource

/** Serve static resources from the class loader.
 *  This implementation overrides de `getResource` method to make it work
 *  in an OSGi container by looking for resrouces in the context of the bundle
 *  class loader
 *
 *  @author Lucas Satabin
 */
trait WebClient {
  this: WebApp =>

  import FileUtils._

  def webClient: Route =
    pathEndOrSingleSlash {
      // look for index,html
      response(getResource("webapp/index.html"), "html")
    } ~
    unmatchedPath { p =>
      response(getResource(s"webapp$p"), new File(p.toString).extension)
    }

  private def response(stream: Option[Array[Byte]], ext: String): Route = stream match {
    case None =>
      reject()
    case Some(stream) =>
      val mime = MediaTypes.forExtension(ext).getOrElse(MediaTypes.`application/octet-stream`)
      respondWithMediaType(mime) {
        import BasicMarshallers._
        complete(stream)
      }
  }

  private def getResource(path: String): Option[Array[Byte]] =
    Option(context.getBundle.getResource(path)) flatMap { url =>
      url.getProtocol match {
        case "jar" | "bundle"  =>
          val is = new PushbackInputStream(url.openStream)
          try {
            is.available
            val first = is.read()
            if(first == -1) {
              // this is an empty stream representing a directory entry or an empty file
              // we never serve directory entries nor empty files, only files with some content.
              is.close
              None
            } else {
              // unread the first byte and return the input stream
              is.unread(first)
              Option(Resource.fromInputStream(is).byteArray)
            }
          } catch {
            case _: Exception => None
          }
        case "file" =>
          if(new File(url.toURI).isFile)
            Option(Resource.fromInputStream(url.openStream).byteArray)
          else
            None
        case _ =>
          None
      }
    }

}

