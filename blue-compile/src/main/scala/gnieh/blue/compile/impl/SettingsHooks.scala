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

import gnieh.sohva.async.entities.EntityManager

import org.osgi.service.log.LogService

import com.typesafe.config.Config

import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext.Implicits.global

/** This hooks creates the compiler settings when a paper is created
 *
 *  @author Lucas Satabin
 */
class CreateSettingsHook(config: Config, logger: Logger) extends PaperCreated {

  val couchConfig = new CouchConfiguration(config)

  def defaultSettings(paperId: String): CompilerSettings =
    CompilerSettings(
      s"$paperId:compiler",
      config.getString("compiler.default"),
      config.getBoolean("compiler.synctex"),
      config.getDuration("compiler.timeout", TimeUnit.SECONDS).toInt,
      config.getDuration("compiler.interval", TimeUnit.SECONDS).toInt
    )

  def afterCreate(paperId: String, manager: EntityManager): Unit = {
    // after creation of a paper, save default settings
    manager.saveComponent(paperId, defaultSettings(paperId)) recover {
      case t =>
        logger.log(LogService.LOG_ERROR, s"Unable to create paper settings for paper $paperId", t)
    }
  }

}
