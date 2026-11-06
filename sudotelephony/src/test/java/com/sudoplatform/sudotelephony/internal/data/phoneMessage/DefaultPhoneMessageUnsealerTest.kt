/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.UnsealedPhoneMessages
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [DefaultPhoneMessageUnsealer]: a sealed message carries no
 * key identifier, so the key is resolved from the phone number's key ring, restricted to the
 * keys this device holds the private half of.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultPhoneMessageUnsealerTest : BaseTests() {
    private val mockPhoneNumberService by before { mock<PhoneNumberService>() }
    private val mockServiceKeyManager by before { mock<ServiceKeyManager>() }
    private val mockUnsealer by before { mock<Unsealer>() }

    private val instanceUnderTest by before {
        DefaultPhoneMessageUnsealer(
            phoneNumberService = mockPhoneNumberService,
            serviceKeyManager = mockServiceKeyManager,
            unsealer = mockUnsealer,
            logger = mockLogger,
        )
    }

    private fun stubNumberWithKeys(
        keyIds: List<String>,
        heldKeyIds: Set<String> = keyIds.toSet(),
    ) {
        mockPhoneNumberService.stub {
            onBlocking { get(any()) } doReturn EntityDataFactory.phoneNumberEntity(keyIds = keyIds)
        }
        mockServiceKeyManager.stub {
            on { privateKeyExists(any()) } doReturn false
        }
        heldKeyIds.forEach { held ->
            mockServiceKeyManager.stub {
                on { privateKeyExists(eq(held)) } doReturn true
            }
        }
    }

    @Test
    fun `unseal() should unseal both sealed fields with the number's key`() =
        runTest {
            stubNumberWithKeys(listOf("mockKeyId"))
            mockUnsealer.stub {
                on { unseal(any(), eq(EntityDataFactory.SEALED_BODY), any()) } doReturn "Hello there"
                on {
                    unseal(any(), eq(EntityDataFactory.SEALED_REMOTE_NUMBER), any())
                } doReturn "+14155559876"
            }

            val result =
                instanceUnderTest.unseal(
                    "mockPhoneNumberId",
                    listOf(EntityDataFactory.sealedPhoneMessageEntity()),
                )

            result.failed shouldBe emptyList()
            result.unsealed.size shouldBe 1
            result.unsealed.first().body shouldBe "Hello there"
            result.unsealed.first().remotePhoneNumber shouldBe "+14155559876"
            // Plaintext fields pass through untouched.
            result.unsealed.first().localPhoneNumber shouldBe "+14155550123"
            result.unsealed.first().segmentCount shouldBe 1
        }

    @Test
    fun `unseal() should leave a withheld remote number null without unsealing it`() =
        runTest {
            stubNumberWithKeys(listOf("mockKeyId"))
            mockUnsealer.stub {
                on { unseal(any(), eq(EntityDataFactory.SEALED_BODY), any()) } doReturn "body"
            }

            val result =
                instanceUnderTest.unseal(
                    "mockPhoneNumberId",
                    listOf(EntityDataFactory.sealedPhoneMessageEntity(remotePhoneNumber = null)),
                )

            result.unsealed.first().remotePhoneNumber shouldBe null
        }

    @Test
    fun `unseal() should pick the key ring key this device holds`() =
        runTest {
            // The ring lists two keys but only the second was generated on this device.
            stubNumberWithKeys(listOf("otherDeviceKey", "thisDeviceKey"), setOf("thisDeviceKey"))
            mockUnsealer.stub {
                on { unseal(any(), any(), any()) } doReturn "x"
            }

            instanceUnderTest.unseal(
                "mockPhoneNumberId",
                listOf(EntityDataFactory.sealedPhoneMessageEntity()),
            )

            verify(mockUnsealer).unseal(
                eq("thisDeviceKey"),
                eq(EntityDataFactory.SEALED_REMOTE_NUMBER),
                any(),
            )
        }

    @Test
    fun `unseal() should resolve the key once for a whole page`() =
        runTest {
            stubNumberWithKeys(listOf("mockKeyId"))
            mockUnsealer.stub {
                on { unseal(any(), any(), any()) } doReturn "x"
            }

            instanceUnderTest.unseal(
                "mockPhoneNumberId",
                listOf(
                    EntityDataFactory.sealedPhoneMessageEntity(id = "first"),
                    EntityDataFactory.sealedPhoneMessageEntity(id = "second"),
                    EntityDataFactory.sealedPhoneMessageEntity(id = "third"),
                ),
            )

            // One key ring read for three messages, not one per message.
            verify(mockPhoneNumberService).get(eq("mockPhoneNumberId"))
        }

    @Test
    fun `unseal() should not read the key ring for an empty page`() =
        runTest {
            instanceUnderTest.unseal("mockPhoneNumberId", emptyList()) shouldBe
                UnsealedPhoneMessages(unsealed = emptyList(), failed = emptyList())

            verifyNoInteractions(mockPhoneNumberService)
            verifyNoInteractions(mockServiceKeyManager)
        }

    @Test
    fun `unseal() should throw PhoneNumberNotFoundException when the number is gone`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { get(any()) } doReturn null
            }

            shouldThrow<PhoneMessageException.PhoneNumberNotFoundException> {
                instanceUnderTest.unseal(
                    "mockPhoneNumberId",
                    listOf(EntityDataFactory.sealedPhoneMessageEntity()),
                )
            }
        }

    @Test
    fun `unseal() should throw KeyNotFoundException when this device holds no key of the ring`() =
        runTest {
            stubNumberWithKeys(listOf("otherDeviceKey"), heldKeyIds = emptySet())

            shouldThrow<PhoneMessageException.KeyNotFoundException> {
                instanceUnderTest.unseal(
                    "mockPhoneNumberId",
                    listOf(EntityDataFactory.sealedPhoneMessageEntity()),
                )
            }
        }

    @Test
    fun `unseal() should return a decryption failure as a partial wrapped in UnsealingFailedException`() =
        runTest {
            stubNumberWithKeys(listOf("mockKeyId"))
            val decryptionFailure = Unsealer.UnsealerException.SealedDataTooShortException("too short")
            mockUnsealer.stub {
                on { unseal(any(), any(), any()) } doThrow decryptionFailure
            }

            val result =
                instanceUnderTest.unseal(
                    "mockPhoneNumberId",
                    listOf(EntityDataFactory.sealedPhoneMessageEntity()),
                )

            result.unsealed shouldBe emptyList()
            result.failed.size shouldBe 1
            result.failed.first().partial shouldBe EntityDataFactory.partialPhoneMessageEntity()
            val cause = result.failed.first().cause
            (cause is PhoneMessageException.UnsealingFailedException) shouldBe true
            cause.cause shouldBe decryptionFailure
        }

    @Test
    fun `unseal() should unseal the rest of the page when one message fails`() =
        runTest {
            stubNumberWithKeys(listOf("mockKeyId"))
            mockUnsealer.stub {
                on { unseal(any(), any(), any()) } doReturn "x"
                on {
                    unseal(any(), eq("corruptBody"), any())
                } doThrow Unsealer.UnsealerException.SealedDataTooShortException("too short")
            }

            val result =
                instanceUnderTest.unseal(
                    "mockPhoneNumberId",
                    listOf(
                        EntityDataFactory.sealedPhoneMessageEntity(id = "first"),
                        EntityDataFactory.sealedPhoneMessageEntity(id = "corrupt", body = "corruptBody"),
                        EntityDataFactory.sealedPhoneMessageEntity(id = "third"),
                    ),
                )

            result.unsealed.map { it.id } shouldBe listOf("first", "third")
            result.failed.map { it.partial.id } shouldBe listOf("corrupt")
        }
}
