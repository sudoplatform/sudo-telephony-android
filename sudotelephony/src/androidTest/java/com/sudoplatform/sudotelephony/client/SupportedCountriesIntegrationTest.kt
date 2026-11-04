/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import io.kotlintest.matchers.collections.shouldContainAll
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of
 * [com.sudoplatform.sudotelephony.SudoTelephonyClient.getSupportedCountries] against a
 * deployed telephony service.
 */
@RunWith(AndroidJUnit4::class)
class SupportedCountriesIntegrationTest : BaseIntegrationTest() {
    @Test
    fun getSupportedCountriesShouldReturnIsoAlpha2CountryCodes() =
        runBlocking {
            val countries = telephonyClient.getSupportedCountries()

            countries shouldNotBe null
            countries.isNotEmpty() shouldBe true
            countries.forEach { code ->
                code shouldBe code.uppercase()
                code.length shouldBe 2
            }
        }

    @Test
    fun getSupportedCountriesShouldIncludeTheUnitedStates() =
        runBlocking {
            val countries = telephonyClient.getSupportedCountries()

            countries shouldContainAll listOf("US")
        }

    @Test
    fun getSupportedCountriesShouldBeStableAcrossCalls() =
        runBlocking {
            val first = telephonyClient.getSupportedCountries()
            val second = telephonyClient.getSupportedCountries()

            second shouldBe first
        }
}
