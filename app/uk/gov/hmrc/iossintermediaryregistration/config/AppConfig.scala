/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.iossintermediaryregistration.config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject()(config: Configuration) {

  val appName: String = config.get[String]("appName")

  val registrationStatusTtl: Long = config.get[Long]("mongodb.timeToLiveInHours")
  val saveForLaterTtl: Long = config.get[Long]("mongodb.timeToLiveInDays")

  val maxRetryCount: Int = config.get[Int]("features.maxRetryCount")
  val delay: Int = config.get[Int]("features.delay")

  val encryptionKey: String = config.get[String]("mongodb.encryption.key")

  val intermediaryEnrolment: String = config.get[String]("intermediaryEnrolment")
  val intermediaryEnrolmentKey: String = config.get[String]("intermediaryEnrolmentKey")
}
