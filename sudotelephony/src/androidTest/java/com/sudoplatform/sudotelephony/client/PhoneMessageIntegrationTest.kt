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
import com.sudoplatform.sudotelephony.types.SortOrder
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [com.sudoplatform.sudotelephony.SudoTelephonyClient.getPhoneMessage]
 * and [com.sudoplatform.sudotelephony.SudoTelephonyClient.listPhoneMessages] against a
 * deployed telephony service.
 *
 * Messages only exist once the telephony provider delivers an inbound SMS, which a test
 * cannot cause. These tests therefore cover the reachable contract: an empty inbox, the
 * filters, and the error paths. Unsealing itself is covered by the unit tests, which seal
 * values exactly the way the service seals them.
 */
@RunWith(AndroidJUnit4::class)
class PhoneMessageIntegrationTest : BaseIntegrationTest() {
    private val countryCode = optionalArgument("COUNTRY_CODE", "US")
    private val areaCode = optionalArgument("AREA_CODE", "415")

    /** Provisions a number and waits for it to become active, skipping when none is offered. */
    private suspend fun activeNumber(): PhoneNumber {
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
                return awaitState(number.id) { it == PhoneNumberState.ACTIVE }
            } catch (e: SudoTelephonyClient.PhoneNumberException.PhoneNumberUnavailableException) {
                logger.info("${available.phoneNumber} was taken after the search, trying the next")
            }
        }
        throw AssertionError("Every number offered in $countryCode-$areaCode was already taken")
    }

    @Test
    fun listPhoneMessagesShouldReturnAnEmptyInboxForANewNumber() =
        runBlocking {
            val number = activeNumber()

            val page =
                telephonyClient.listPhoneMessages(
                    ListPhoneMessagesInput(phoneNumberId = number.id),
                )

            // A number that has never received a message has an empty inbox and no more pages.
            page shouldNotBe null
            page.items shouldBe emptyList()
            page.nextToken shouldBe null
        }

    @Test
    fun listPhoneMessagesShouldAcceptEveryFilter() =
        runBlocking {
            val number = activeNumber()

            val page =
                telephonyClient.listPhoneMessages(
                    ListPhoneMessagesInput(
                        phoneNumberId = number.id,
                        conversationId = "tel-cnv-${UUID.randomUUID()}",
                        sortOrder = SortOrder.ASC,
                        limit = 10,
                    ),
                )

            // An unknown conversation is empty rather than an error.
            page.items shouldBe emptyList()
        }

    @Test
    fun listPhoneMessagesShouldReturnAnEmptyInboxForANumberTheCallerDoesNotOwn() =
        runBlocking {
            // The service filters the inbox by owner rather than rejecting the request, so a
            // number the caller does not own reads as empty and leaks nothing.
            val page =
                telephonyClient.listPhoneMessages(
                    ListPhoneMessagesInput(phoneNumberId = "tel-pnm-${UUID.randomUUID()}"),
                )

            page.items shouldBe emptyList()
            page.nextToken shouldBe null
        }

    @Test
    fun getPhoneMessageShouldReturnNullForAnUnknownMessage() =
        runBlocking {
            telephonyClient.getPhoneMessage("tel-msg-${UUID.randomUUID()}") shouldBe null
        }
}
