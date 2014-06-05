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
package common
package impl

import spray.routing.RequestContext
import spray.http.{
  HttpRequest,
  Uri
}

import net.tanesha.recaptcha.{
  ReCaptchaImpl,
  ReCaptchaResponse
}

/** @author Lucas Satabin
 *
 */
class ReCaptchaUtilImpl(configuration: BlueConfiguration) extends ReCaptcha {

  val RECAPTCHA_RESPONSE_FIELD = "recaptcha_response_field"
  val RECAPTCHA_CHALLENGE_FIELD = "recaptcha_challenge_field"

  /** ReCaptcha is enabled if the private key is configured */
  val enabled =
    configuration.recaptchaPrivateKey.isDefined

  def apply(context: RequestContext) =
    (context, configuration.recaptchaPrivateKey) match {
      case (RequestContext(HttpRequest(_, Uri(_, _, _, query, _), headers, _, _), _, _), Some(privatekey)) =>
        val challenge = query.get(RECAPTCHA_CHALLENGE_FIELD)
        val response = query.get(RECAPTCHA_RESPONSE_FIELD)
        val remoteIp = headers.find(_.name == "X-Real-IP")

        (challenge, response, remoteIp) match {
          case (Some(c), Some(r), Some(ip)) =>
            val reCaptcha = new ReCaptchaImpl

            // no exception will be thrown because with checked that the private key
            // is defined
            reCaptcha.setPrivateKey(privatekey)

            val reCaptchaResponse =
              reCaptcha.checkAnswer(ip.value, c, r)

            reCaptchaResponse.isValid
          case _ => false
        }
      case (_, None) =>
        // if not enabled, then the request is authorized
        true
    }

}
