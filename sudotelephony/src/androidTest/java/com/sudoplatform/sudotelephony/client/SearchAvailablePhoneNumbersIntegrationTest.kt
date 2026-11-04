/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.types.PhoneNumberType
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of
 * [com.sudoplatform.sudotelephony.SudoTelephonyClient.searchAvailablePhoneNumbers] against a
 * deployed telephony service.
 */
@RunWith(AndroidJUnit4::class)
class SearchAvailablePhoneNumbersIntegrationTest : BaseIntegrationTest() {
    private val countryCode = optionalArgument("COUNTRY_CODE", "US")
    private val areaCode = optionalArgument("AREA_CODE", "415")

    /** The number of results the service returns when the request sets no limit. */
    private val defaultLimit = 10

    /** The largest limit the service accepts. */
    private val maxLimit = 20

    /** How long the service keeps a search for provisioning. */
    private val searchLifetimeMs = 15 * 60 * 1000L

    /** Local US numbers in the requested area code, in E.164 form. */
    private val localNumberInAreaCode = Regex("""^\+1$areaCode[2-9]\d{6}$""")

    @Test
    fun searchAvailablePhoneNumbersShouldReturnASearchForTheRequestedCriteria() =
        runBlocking {
            val search =
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(
                        countryCode = countryCode,
                        areaCode = areaCode,
                    ),
                )

            search shouldNotBe null
            search.id.startsWith("tel-pns-") shouldBe true
            search.countryCode shouldBe countryCode
            search.areaCode shouldBe areaCode
            search.owner.isNotBlank() shouldBe true
            search.version shouldBe 1
            // A service-side invariant, so it does not depend on the device clock.
            (search.expiresAt.time - search.createdAt.time) shouldBe searchLifetimeMs
            (search.results.size <= defaultLimit) shouldBe true
        }

    @Test
    fun searchAvailablePhoneNumbersShouldOnlyReturnLocalNumbersInTheRequestedAreaCode() =
        runBlocking {
            val search =
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(
                        countryCode = countryCode,
                        areaCode = areaCode,
                    ),
                )

            // How many numbers the provider has available is not guaranteed, so this
            // asserts what every returned number must look like rather than that any
            // were returned.
            search.results.forEach { available ->
                available.countryCode shouldBe countryCode
                available.numberType shouldBe PhoneNumberType.LOCAL
                localNumberInAreaCode.matches(available.phoneNumber) shouldBe true
            }
        }

    @Test
    fun searchAvailablePhoneNumbersShouldHonourTheRequestedLimit() =
        runBlocking {
            val limit = 2
            val search =
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(
                        countryCode = countryCode,
                        areaCode = areaCode,
                        limit = limit,
                    ),
                )

            (search.results.size <= limit) shouldBe true
        }

    @Test
    fun searchAvailablePhoneNumbersShouldAcceptTheMaximumLimit() =
        runBlocking {
            val search =
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(
                        countryCode = countryCode,
                        areaCode = areaCode,
                        limit = maxLimit,
                    ),
                )

            (search.results.size <= maxLimit) shouldBe true
        }

    @Test
    fun searchAvailablePhoneNumbersShouldReturnNumbersWithoutDuplicates() =
        runBlocking {
            val search =
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(
                        countryCode = countryCode,
                        areaCode = areaCode,
                    ),
                )

            val numbers = search.results.map { it.phoneNumber }
            numbers.distinct().size shouldBe numbers.size
        }

    @Test
    fun searchAvailablePhoneNumbersShouldRejectAnUnsupportedCountry() =
        runBlocking {
            shouldThrow<SudoTelephonyClient.PhoneNumberSearchException.UnsupportedCountryException> {
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(
                        countryCode = "ZZ",
                        areaCode = areaCode,
                    ),
                )
            }
            Unit
        }

    @Test
    fun searchAvailablePhoneNumbersShouldRejectAnUnknownAreaCode() =
        runBlocking {
            shouldThrow<SudoTelephonyClient.PhoneNumberSearchException.InvalidAreaCodeException> {
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(
                        countryCode = countryCode,
                        areaCode = "000",
                    ),
                )
            }
            Unit
        }

    @Test
    fun searchAvailablePhoneNumbersShouldRejectAMissingAreaCode() =
        runBlocking {
            shouldThrow<SudoTelephonyClient.PhoneNumberSearchException.InvalidArgumentException> {
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(countryCode = countryCode),
                )
            }
            Unit
        }

    @Test
    fun searchAvailablePhoneNumbersShouldRejectALimitAboveTheMaximum() =
        runBlocking {
            shouldThrow<SudoTelephonyClient.PhoneNumberSearchException.LimitExceededException> {
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(
                        countryCode = countryCode,
                        areaCode = areaCode,
                        limit = 21,
                    ),
                )
            }
            Unit
        }
}
