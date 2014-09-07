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

import common._

import spray.routing.Route

/** Returns the list of compilers that are currently available in \BlueLaTeX
 *  and that can be used to compile the papers
 *
 *  @author Lucas Satabin
 */
trait GetCompilers {
  this: CompilationApi =>

  import OsgiUtils._

  def getCompilers: Route = requireUser { _ =>
    complete(context.getAll[Compiler].map(_.name).toList.sorted)
  }

}

