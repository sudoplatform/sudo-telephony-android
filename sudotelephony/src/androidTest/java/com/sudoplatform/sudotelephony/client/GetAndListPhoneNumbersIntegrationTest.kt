/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
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
    private val countryCode = optionalArgument("COUNTRY_CODE", "US")
    private val areaCode = optionalArgument("AREA_CODE", "415")

    /** Provisions a number, skipping the test when the provider offers none. */
    private suspend fun provisionANumber(): PhoneNumber {
        val search =
            telephonyClient.searchAvailablePhoneNumbers(
                SearchAvailablePhoneNumbersInput(
                    countryCode = countryCode,
                    areaCode = areaCode,
                ),
            )
        assumeTrue(
            "No numbers offered in $countryCode-$areaCode on this instance",
            search.results.isNotEmpty(),
        )

        for (available in search.results.shuffled()) {
            try {
                val number =
                    telephonyClient.provisionPhoneNumber(
                        ProvisionPhoneNumberInput(
                            searchId = search.id,
                            phoneNumber = available.phoneNumber,
                        ),
                    )
                trackForRelease(number.id)
                return number
            } catch (e: com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException.PhoneNumberUnavailableException) {
                logger.info("${available.phoneNumber} was taken after the search, trying the next")
            }
        }
        throw AssertionError("Every number offered in $countryCode-$areaCode was already taken")
    }

    @Test
    fun getPhoneNumberShouldReturnTheProvisionedNumber() =
        runBlocking {
            val provisioned = provisionANumber()

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
            val provisioned = provisionANumber()
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
            val provisioned = provisionANumber()
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
            val provisioned = provisionANumber()

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
            val provisioned = provisionANumber()
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
}
