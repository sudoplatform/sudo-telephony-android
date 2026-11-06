/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersForSudoIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [com.sudoplatform.sudotelephony.SudoTelephonyClient.getPhoneNumber]
 * and [com.sudoplatform.sudotelephony.SudoTelephonyClient.listPhoneNumbers] against a
 * deployed telephony service.
 */
@RunWith(AndroidJUnit4::class)
class GetAndListPhoneNumbersIntegrationTest : BaseIntegrationTest() {
    @Test
    fun getPhoneNumberShouldReturnTheProvisionedNumber() =
        runBlocking {
            val provisioned = provisionANumber().number

            val fetched = telephonyClient.getPhoneNumber(provisioned.id)

            fetched shouldNotBe null
            fetched!!.id shouldBe provisioned.id
            fetched.phoneNumber shouldBe provisioned.phoneNumber
            fetched.countryCode shouldBe provisioned.countryCode
            fetched.owner shouldBe provisioned.owner
            fetched.keyRingId shouldBe provisioned.keyRingId
            fetched.keyIds shouldBe provisioned.keyIds
        }

    @Test
    fun getPhoneNumberShouldReportTheNumberLeavingProvisioning() =
        runBlocking {
            val provisioned = provisionANumber().number
            provisioned.state shouldBe PhoneNumberState.PROVISIONING

            // The order completes asynchronously, so the state is read until it settles.
            val settled = awaitState(provisioned.id) { it != PhoneNumberState.PROVISIONING }

            settled.state shouldBe PhoneNumberState.ACTIVE
            settled.failureReason shouldBe null
            (settled.version > provisioned.version) shouldBe true
        }

    @Test
    fun getPhoneNumberShouldReturnNullForAnUnknownNumber() =
        runBlocking {
            telephonyClient.getPhoneNumber("tel-pnm-${UUID.randomUUID()}") shouldBe null
        }

    @Test
    fun getPhoneNumberShouldStillReturnAReleasedNumber() =
        runBlocking {
            val provisioned = provisionANumber().number
            awaitState(provisioned.id) { it != PhoneNumberState.PROVISIONING }
            telephonyClient.deprovisionPhoneNumber(provisioned.id)

            // Unlike listPhoneNumbers, a released number is still readable by id.
            val fetched = telephonyClient.getPhoneNumber(provisioned.id)

            fetched shouldNotBe null
            fetched!!.state shouldBe PhoneNumberState.RELEASED
        }

    @Test
    fun listPhoneNumbersShouldIncludeTheProvisionedNumber() =
        runBlocking {
            val provisioned = provisionANumber().number

            val page = telephonyClient.listPhoneNumbers()

            page.items.isNotEmpty() shouldBe true
            page.items.any { it.id == provisioned.id } shouldBe true
        }

    @Test
    fun listPhoneNumbersShouldHonourTheRequestedLimitAndPage() =
        runBlocking {
            provisionANumber()
            provisionANumber()

            val firstPage = telephonyClient.listPhoneNumbers(ListPhoneNumbersInput(limit = 1))

            firstPage.items.size shouldBe 1
            firstPage.nextToken shouldNotBe null

            val secondPage =
                telephonyClient.listPhoneNumbers(
                    ListPhoneNumbersInput(limit = 1, nextToken = firstPage.nextToken),
                )

            secondPage.items.size shouldBe 1
            // Paging advances rather than repeating the first item.
            secondPage.items.first().id shouldNotBe firstPage.items.first().id
        }

    @Test
    fun listPhoneNumbersShouldNotIncludeAReleasedNumber() =
        runBlocking {
            val provisioned = provisionANumber().number
            awaitState(provisioned.id) { it != PhoneNumberState.PROVISIONING }
            telephonyClient.deprovisionPhoneNumber(provisioned.id)

            val page = telephonyClient.listPhoneNumbers()

            page.items.none { it.id == provisioned.id } shouldBe true
        }

    @Test
    fun listPhoneNumbersShouldRejectATokenTheServiceCannotDecode() =
        runBlocking {
            shouldThrow<com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException.InvalidArgumentException> {
                telephonyClient.listPhoneNumbers(ListPhoneNumbersInput(nextToken = "not-a-token"))
            }
            Unit
        }

    @Test
    fun listPhoneNumbersForSudoIdShouldReturnAnEmptyPageForASudoWithNoNumbers() =
        runBlocking {
            val page =
                telephonyClient.listPhoneNumbersForSudoId(
                    ListPhoneNumbersForSudoIdInput(sudoId = UUID.randomUUID().toString()),
                )

            page.items shouldBe emptyList()
            page.nextToken shouldBe null
        }

    @Test
    fun listPhoneNumbersForSudoIdShouldRejectAnEmptySudoId() =
        runBlocking {
            shouldThrow<com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException.InvalidArgumentException> {
                telephonyClient.listPhoneNumbersForSudoId(ListPhoneNumbersForSudoIdInput(sudoId = ""))
            }
            Unit
        }
}
