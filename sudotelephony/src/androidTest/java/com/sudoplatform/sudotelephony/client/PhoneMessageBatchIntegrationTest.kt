/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.types.inputs.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.UpdatePhoneMessagesInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of
 * [com.sudoplatform.sudotelephony.SudoTelephonyClient.deletePhoneMessages] and
 * [com.sudoplatform.sudotelephony.SudoTelephonyClient.updatePhoneMessages] against a
 * deployed telephony service.
 *
 * A test cannot cause an inbound SMS, so there are no real messages to act on. These tests
 * cover what is reachable without one: the batch contract, the size limit, and that an
 * unknown id is reported in the result rather than thrown.
 */
@RunWith(AndroidJUnit4::class)
class PhoneMessageBatchIntegrationTest : BaseIntegrationTest() {
    /** The largest batch the service accepts. */
    private val maxBatchSize = 100

    private fun messageIds(count: Int): List<String> = (1..count).map { "tel-msg-${UUID.randomUUID()}" }

    @Test
    fun deletePhoneMessagesShouldAccountForEveryRequestedId() =
        runBlocking {
            val ids = messageIds(3)

            val result = telephonyClient.deletePhoneMessages(DeletePhoneMessagesInput(ids = ids))

            result shouldNotBe null
            // Whichever bucket the service chooses, every id must be accounted for exactly
            // once and nothing else may appear.
            (result.succeededIds + result.failedIds).sorted() shouldBe ids.sorted()
        }

    @Test
    fun updatePhoneMessagesShouldReportUnknownIdsAsFailedRatherThanThrowing() =
        runBlocking {
            val ids = messageIds(2)

            val result =
                telephonyClient.updatePhoneMessages(
                    UpdatePhoneMessagesInput(ids = ids, seen = true),
                )

            // A message that does not exist is a partial failure, not an exception.
            result.succeededIds shouldBe emptyList()
            result.failedIds.sorted() shouldBe ids.sorted()
        }

    @Test
    fun batchOperationsShouldAcceptAnEmptyBatch() =
        runBlocking {
            val deleted = telephonyClient.deletePhoneMessages(DeletePhoneMessagesInput(ids = emptyList()))
            deleted.succeededIds shouldBe emptyList()
            deleted.failedIds shouldBe emptyList()

            val updated =
                telephonyClient.updatePhoneMessages(
                    UpdatePhoneMessagesInput(ids = emptyList(), seen = true),
                )
            updated.succeededIds shouldBe emptyList()
            updated.failedIds shouldBe emptyList()
        }

    @Test
    fun batchOperationsShouldAcceptTheMaximumBatchSize() =
        runBlocking {
            val result =
                telephonyClient.deletePhoneMessages(
                    DeletePhoneMessagesInput(ids = messageIds(maxBatchSize)),
                )

            (result.succeededIds.size + result.failedIds.size) shouldBe maxBatchSize
        }

    @Test
    fun deletePhoneMessagesShouldRejectABatchAboveTheMaximum() =
        runBlocking {
            shouldThrow<SudoTelephonyClient.PhoneMessageException.LimitExceededException> {
                telephonyClient.deletePhoneMessages(
                    DeletePhoneMessagesInput(ids = messageIds(maxBatchSize + 1)),
                )
            }
            Unit
        }

    @Test
    fun updatePhoneMessagesShouldRejectABatchAboveTheMaximum() =
        runBlocking {
            shouldThrow<SudoTelephonyClient.PhoneMessageException.LimitExceededException> {
                telephonyClient.updatePhoneMessages(
                    UpdatePhoneMessagesInput(ids = messageIds(maxBatchSize + 1), seen = true),
                )
            }
            Unit
        }
}
