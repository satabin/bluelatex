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

import com.typesafe.config.Config

package object common {

  type Logger = org.osgi.service.log.LogService

  type UserInfo = gnieh.sohva.UserInfo

  implicit class MultiMap[Key,Value](val map: Map[Key, Set[Value]]) extends AnyVal {

    def addBinding(key: Key, value: Value): Map[Key, Set[Value]] = map.get(key) match {
      case Some(set) =>
        // add the value to the set
        map.updated(key, set + value)
      case None =>
        // create the binding
        map.updated(key, Set(value))
    }

  }

}

