/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.types.ListAPIResult
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.SortOrder
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesForConversationIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesForPhoneNumberIdInput
import io.kotlintest.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [com.sudoplatform.sudotelephony.SudoTelephonyClient.getPhoneMessage]
 * and the [com.sudoplatform.sudotelephony.SudoTelephonyClient] list methods against a
 * deployed telephony service.
 *
 * Messages only exist once the telephony provider delivers an inbound SMS, which a test
 * cannot cause. These tests therefore cover the reachable contract: an empty inbox, the
 * filters, and the error paths. Unsealing itself is covered by the unit tests, which seal
 * values exactly the way the service seals them.
 */
@RunWith(AndroidJUnit4::class)
class PhoneMessageIntegrationTest : BaseIntegrationTest() {
    /** Provisions a number and waits for it to become active, skipping when none is offered. */
    private suspend fun activeNumber(): PhoneNumber = awaitState(provisionANumber().number.id) { it == PhoneNumberState.Active }

    @Test
    fun listPhoneMessagesForPhoneNumberIdShouldReturnAnEmptyInboxForANewNumber() =
        runBlocking {
            val number = activeNumber()

            val page =
                telephonyClient.listPhoneMessagesForPhoneNumberId(
                    ListPhoneMessagesForPhoneNumberIdInput(phoneNumberId = number.id),
                )

            // A number that has never received a message has an empty inbox and no more pages.
            page shouldBe ListAPIResult.Success(ListAPIResult.ListSuccessResult(emptyList(), null))
        }

    @Test
    fun listPhoneMessagesForPhoneNumberIdShouldAcceptEveryFilter() =
        runBlocking {
            val number = activeNumber()

            val page =
                telephonyClient.listPhoneMessagesForPhoneNumberId(
                    ListPhoneMessagesForPhoneNumberIdInput(
                        phoneNumberId = number.id,
                        sortOrder = SortOrder.ASC,
                        limit = 10,
                    ),
                )

            (page as ListAPIResult.Success).result.items shouldBe emptyList()
        }

    @Test
    fun listPhoneMessagesForConversationIdShouldReturnAnEmptyPageForAnUnknownConversation() =
        runBlocking {
            val page =
                telephonyClient.listPhoneMessagesForConversationId(
                    ListPhoneMessagesForConversationIdInput(
                        conversationId = "tel-cnv-${UUID.randomUUID()}",
                        sortOrder = SortOrder.ASC,
                        limit = 10,
                    ),
                )

            // An unknown conversation is empty rather than an error.
            page shouldBe ListAPIResult.Success(ListAPIResult.ListSuccessResult(emptyList(), null))
        }

    @Test
    fun listPhoneMessagesForPhoneNumberIdShouldReturnAnEmptyInboxForANumberTheCallerDoesNotOwn() =
        runBlocking {
            // The service filters the inbox by owner rather than rejecting the request, so a
            // number the caller does not own reads as empty and leaks nothing.
            val page =
                telephonyClient.listPhoneMessagesForPhoneNumberId(
                    ListPhoneMessagesForPhoneNumberIdInput(phoneNumberId = "tel-pnm-${UUID.randomUUID()}"),
                )

            page shouldBe ListAPIResult.Success(ListAPIResult.ListSuccessResult(emptyList(), null))
        }

    @Test
    fun getPhoneMessageShouldReturnNullForAnUnknownMessage() =
        runBlocking {
            telephonyClient.getPhoneMessage("tel-msg-${UUID.randomUUID()}") shouldBe null
        }
}
