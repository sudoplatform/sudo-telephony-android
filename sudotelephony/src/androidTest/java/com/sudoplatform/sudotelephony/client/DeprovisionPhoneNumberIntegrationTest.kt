/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of
 * [com.sudoplatform.sudotelephony.SudoTelephonyClient.deprovisionPhoneNumber] against a
 * deployed telephony service.
 */
@RunWith(AndroidJUnit4::class)
class DeprovisionPhoneNumberIntegrationTest : BaseIntegrationTest() {
    /**
     * Releases [id] once its provisioning order has completed, which the service requires
     * before a number can be released.
     *
     * @return The released [PhoneNumber].
     */
    private suspend fun releaseWhenActive(id: String): PhoneNumber {
        awaitState(id) { it == PhoneNumberState.ACTIVE }
        return telephonyClient.deprovisionPhoneNumber(id)
    }

    @Test
    fun deprovisionPhoneNumberShouldReleaseAProvisionedNumber() =
        runBlocking {
            val provisioned = provisionANumber().number

            val released = releaseWhenActive(provisioned.id)

            released shouldNotBe null
            released.id shouldBe provisioned.id
            released.phoneNumber shouldBe provisioned.phoneNumber
            released.state shouldBe PhoneNumberState.RELEASED
            // The allowance is returned at once, so the record is updated immediately.
            (released.version > provisioned.version) shouldBe true
        }

    @Test
    fun deprovisionPhoneNumberShouldBeSafeToRepeat() =
        runBlocking {
            val provisioned = provisionANumber().number
            val released = releaseWhenActive(provisioned.id)

            // Releasing a released number returns it unchanged.
            val again = telephonyClient.deprovisionPhoneNumber(provisioned.id)

            again.id shouldBe released.id
            again.state shouldBe PhoneNumberState.RELEASED
        }

    @Test
    fun deprovisionPhoneNumberShouldRejectANumberStillBeingProvisioned() =
        runBlocking {
            val provisioned = provisionANumber().number
            // The number is reported as PROVISIONING the instant it is created.
            provisioned.state shouldBe PhoneNumberState.PROVISIONING

            val exception =
                shouldThrow<SudoTelephonyClient.PhoneNumberException.InvalidPhoneNumberStateException> {
                    telephonyClient.deprovisionPhoneNumber(provisioned.id)
                }

            // The service reports the state that blocked the release.
            exception.state shouldBe PhoneNumberState.PROVISIONING
        }

    @Test
    fun deprovisionPhoneNumberShouldRejectAnUnknownNumber() =
        runBlocking {
            shouldThrow<SudoTelephonyClient.PhoneNumberException.PhoneNumberNotFoundException> {
                telephonyClient.deprovisionPhoneNumber("tel-pn-${UUID.randomUUID()}")
            }
            Unit
        }
}
