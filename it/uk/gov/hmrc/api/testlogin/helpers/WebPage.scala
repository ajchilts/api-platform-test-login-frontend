/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.api.testlogin.helpers

import org.openqa.selenium.WebDriver
import org.scalatestplus.selenium.{Page, WebBrowser}
import org.scalatest.matchers.should.Matchers

case class Link(href: String, text: String)

trait WebLink extends Page with WebBrowser with Matchers {
  implicit val webDriver: WebDriver = Env.driver

  override def toString = this.getClass.getSimpleName
}

trait WebPage extends WebLink {

  def isCurrentPage: Boolean

  def heading = tagName("h1").element.text

  def bodyText = tagName("body").element.text

}
