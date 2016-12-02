/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.api.testlogin.connectors

import javax.inject.Inject

import play.api.libs.ws.WSClient
import play.api.mvc.Session.COOKIE_NAME
import uk.gov.hmrc.api.testlogin.models.{TestOrganisation, TestIndividual, TestUser}
import uk.gov.hmrc.crypto.ApplicationCrypto.SessionCookieCrypto
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.domain.{EmpRef, SaUtr}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AuthLoginStubConnector {
  val serviceUrl: String
  val wsClient: WSClient

  def createSession(testUser: TestUser): Future[String] = {
    val form = GovernmentGatewayForm.from(testUser)

    wsClient.url(s"$serviceUrl/auth-login-stub/gg-sign-in").withFollowRedirects(false).post(form.toMap) map { response =>
      response.status match {
        case 303 =>
          val encryptedCookie = response.cookie(COOKIE_NAME).flatMap(_.value)
            .getOrElse(throw new RuntimeException(s"Cookie not present in response from auth-login-stub userId=${testUser.username}"))
          decrypt(COOKIE_NAME, encryptedCookie)
        case _ =>
          throw new RuntimeException(s"Invalid response from auth-login-stub userId=${testUser.username} status=${response.status} body=${response.body}")
      }
    }
  }

  private def decrypt(cookieName: String, cookieValue: String) = {
    s"$cookieName=" + SessionCookieCrypto.decrypt(Crypted(cookieValue)).value
  }
}

class AuthLoginStubConnectorImpl @Inject()(override val wsClient: WSClient) extends AuthLoginStubConnector with ServicesConfig {
  override val serviceUrl: String = baseUrl("auth-login-stub")
}

case class GovernmentGatewayForm(
                                  authorityId: String,
                                  affinityGroup: String,
                                  nino: Option[String],
                                  enrolment: Seq[Enrolment],
                                  confidenceLevel: Int = ConfidenceLevel.L300.level,
                                  credentialStrength: String = "strong",
                                  redirectionUrl: String = "fakeredirectUrl") {

  def toMap: Map[String, Seq[String]] = Map(
    "authorityId" -> Seq(authorityId),
    "affinityGroup" -> Seq(affinityGroup),
    "nino" -> nino.toSeq,
    "confidenceLevel" -> Seq(confidenceLevel.toString),
    "credentialStrength" -> Seq(credentialStrength),
    "redirectionUrl" -> Seq(redirectionUrl)
  ) ++ enrolmentsToMap()

  private def enrolmentsToMap(): Map[String, Seq[String]] = {
    enrolment.zipWithIndex flatMap  {
      case (enrol, index) =>
        val identifiers = enrol.identifiers.zipWithIndex flatMap {
          case (identifier, n) => Map(
            s"enrolment[$index].taxIdentifier[$n].name" -> Seq(identifier.name),
            s"enrolment[$index].taxIdentifier[$n].value" -> Seq(identifier.value)
          )
        }
        Map(
          s"enrolment[$index].name" -> Seq(enrol.name),
          s"enrolment[$index].state" -> Seq(enrol.state)) ++ identifiers
    } toMap
  }
}

case class Enrolment(name: String, identifiers: Seq[TaxIdentifier], state: String = "Activated")

case class TaxIdentifier(name: String, value: String)

object GovernmentGatewayForm {

  def from(testUser: TestUser): GovernmentGatewayForm = testUser match {
    case individual: TestIndividual =>
      GovernmentGatewayForm(individual.username, "Individual", Some(individual.nino.value), Seq(
        Enrolment("IR-SA", Seq(TaxIdentifier("UTR", individual.saUtr.toString())))))

    case organisation: TestOrganisation =>
      GovernmentGatewayForm(organisation.username, "Organisation", None, Seq(
        Enrolment("IR-SA", utr(organisation.saUtr.toString())),
        Enrolment("IR-CT", utr(organisation.ctUtr.toString())),
        Enrolment("HMCE-VATDEC-ORG", vrn(organisation.vrn.toString())),
        Enrolment("IR-PAYE", paye(organisation.empRef))))
  }

  def utr(saUtr: String) = Seq(TaxIdentifier("UTR", saUtr))
  def vrn(vrn: String) = Seq(TaxIdentifier("VATRegNo", vrn))
  def paye(empRef: EmpRef) = Seq(
    TaxIdentifier("TaxOfficeNumber", empRef.taxOfficeNumber),
    TaxIdentifier("TaxOfficeReference", empRef.taxOfficeReference))
}
