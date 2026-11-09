/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import io.kotlintest.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/**
 * Test the operation of
 * [com.sudoplatform.sudotelephony.SudoTelephonyClient.getConfigurationData] against a
 * deployed telephony service.
 */
@RunWith(AndroidJUnit4::class)
class ConfigurationDataIntegrationTest : BaseIntegrationTest() {
    @Test
    fun getConfigurationDataShouldReturnConsistentLimits() =
        runBlocking {
            val config = telephonyClient.getConfigurationData()

            (config.phoneNumberSearchDefaultLimit in 1..config.phoneNumberSearchMaxLimit) shouldBe true
            (config.listDefaultLimit in 1..config.listMaxLimit) shouldBe true
            (config.phoneNumberSearchLifetime > Duration.ZERO) shouldBe true
            (config.deletePhoneMessagesLimit > 0) shouldBe true
            (config.updatePhoneMessagesLimit > 0) shouldBe true
        }
}
