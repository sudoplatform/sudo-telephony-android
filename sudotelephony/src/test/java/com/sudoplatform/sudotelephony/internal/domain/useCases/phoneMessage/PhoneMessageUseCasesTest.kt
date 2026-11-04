/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesOutput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageBatchResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SortOrderEntity
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [GetPhoneMessageUseCase] and [ListPhoneMessagesUseCase]: each
 * retrieves sealed messages and hands them to the [PhoneMessageUnsealer] to be made readable.
 */
@RunWith(RobolectricTestRunner::class)
class PhoneMessageUseCasesTest : BaseTests() {
    private val mockPhoneMessageService by before { mock<PhoneMessageService>() }
    private val mockUnsealer by before { mock<PhoneMessageUnsealer>() }

    private val getUseCase by before {
        GetPhoneMessageUseCase(mockPhoneMessageService, mockUnsealer, mockLogger)
    }

    private val listUseCase by before {
        ListPhoneMessagesUseCase(mockPhoneMessageService, mockUnsealer, mockLogger)
    }

    private val deleteUseCase by before {
        DeletePhoneMessagesUseCase(mockPhoneMessageService, mockLogger)
    }

    private val updateUseCase by before {
        UpdatePhoneMessagesUseCase(mockPhoneMessageService, mockLogger)
    }

    @Test
    fun `GetPhoneMessageUseCase execute() should return the unsealed message`() =
        runTest {
            val unsealed = EntityDataFactory.phoneMessageEntity()
            mockPhoneMessageService.stub {
                onBlocking { get(any()) } doReturn EntityDataFactory.sealedPhoneMessageEntity()
            }
            mockUnsealer.stub {
                onBlocking { unseal(any(), any()) } doReturn listOf(unsealed)
            }

            getUseCase.execute("mockMessageId") shouldBe unsealed

            verify(mockPhoneMessageService).get(eq("mockMessageId"))
            verify(mockUnsealer).unseal(eq("mockPhoneNumberId"), any())
        }

    @Test
    fun `GetPhoneMessageUseCase execute() should return null without unsealing when absent`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { get(any()) } doReturn null
            }

            getUseCase.execute("mockMessageId") shouldBe null

            verifyNoInteractions(mockUnsealer)
        }

    @Test
    fun `GetPhoneMessageUseCase execute() should propagate an unsealing failure`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { get(any()) } doReturn EntityDataFactory.sealedPhoneMessageEntity()
            }
            mockUnsealer.stub {
                onBlocking {
                    unseal(any(), any())
                } doThrow PhoneMessageException.KeyNotFoundException("no key")
            }

            shouldThrow<PhoneMessageException.KeyNotFoundException> {
                getUseCase.execute("mockMessageId")
            }
        }

    @Test
    fun `ListPhoneMessagesUseCase execute() should return the unsealed page and next token`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { list(any()) } doReturn
                    ListPhoneMessagesOutput(
                        items = listOf(EntityDataFactory.sealedPhoneMessageEntity()),
                        nextToken = "mockNextToken",
                    )
            }
            mockUnsealer.stub {
                onBlocking {
                    unseal(any(), any())
                } doReturn listOf(EntityDataFactory.phoneMessageEntity(body = "Hello there"))
            }

            val result =
                listUseCase.execute(
                    ListPhoneMessagesUseCaseInput(phoneNumberId = "mockPhoneNumberId"),
                )

            result.items.size shouldBe 1
            result.items.first().body shouldBe "Hello there"
            result.nextToken shouldBe "mockNextToken"
        }

    @Test
    fun `ListPhoneMessagesUseCase execute() should map every filter onto the service input`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { list(any()) } doReturn ListPhoneMessagesOutput(items = emptyList())
            }
            mockUnsealer.stub {
                onBlocking { unseal(any(), any()) } doReturn emptyList()
            }

            listUseCase.execute(
                ListPhoneMessagesUseCaseInput(
                    phoneNumberId = "mockPhoneNumberId",
                    conversationId = "mockConversationId",
                    sortOrder = SortOrderEntity.ASC,
                    limit = 4,
                    nextToken = "token",
                ),
            )

            verify(mockPhoneMessageService).list(
                check {
                    it.phoneNumberId shouldBe "mockPhoneNumberId"
                    it.conversationId shouldBe "mockConversationId"
                    it.sortOrder shouldBe SortOrderEntity.ASC
                    it.limit shouldBe 4
                    it.nextToken shouldBe "token"
                },
            )
        }

    @Test
    fun `ListPhoneMessagesUseCase execute() should return an empty page unchanged`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { list(any()) } doReturn ListPhoneMessagesOutput(items = emptyList())
            }
            mockUnsealer.stub {
                onBlocking { unseal(any(), any()) } doReturn emptyList()
            }

            val result =
                listUseCase.execute(
                    ListPhoneMessagesUseCaseInput(phoneNumberId = "mockPhoneNumberId"),
                )

            result.items shouldBe emptyList()
            result.nextToken shouldBe null
        }

    @Test
    fun `ListPhoneMessagesUseCase execute() should propagate a domain exception unchanged`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking {
                    list(any())
                } doThrow PhoneMessageException.PhoneNumberNotFoundException("gone")
            }

            shouldThrow<PhoneMessageException.PhoneNumberNotFoundException> {
                listUseCase.execute(
                    ListPhoneMessagesUseCaseInput(phoneNumberId = "mockPhoneNumberId"),
                )
            }
        }

    @Test
    fun `DeletePhoneMessagesUseCase execute() should pass the ids and return the outcome`() =
        runTest {
            val outcome =
                PhoneMessageBatchResultEntity(
                    succeededIds = listOf("deleted"),
                    failedIds = listOf("missing"),
                )
            mockPhoneMessageService.stub {
                onBlocking { delete(any()) } doReturn outcome
            }

            deleteUseCase.execute(listOf("deleted", "missing")) shouldBe outcome

            verify(mockPhoneMessageService).delete(
                check { it.ids shouldBe listOf("deleted", "missing") },
            )
        }

    @Test
    fun `DeletePhoneMessagesUseCase execute() should propagate a domain exception unchanged`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { delete(any()) } doThrow PhoneMessageException.LimitExceededException("too many")
            }

            shouldThrow<PhoneMessageException.LimitExceededException> {
                deleteUseCase.execute(listOf("a"))
            }
        }

    @Test
    fun `UpdatePhoneMessagesUseCase execute() should pass the ids and the read state`() =
        runTest {
            val outcome = PhoneMessageBatchResultEntity(succeededIds = listOf("a"), failedIds = emptyList())
            mockPhoneMessageService.stub {
                onBlocking { updateSeen(any()) } doReturn outcome
            }

            updateUseCase.execute(
                UpdatePhoneMessagesUseCaseInput(ids = listOf("a"), seen = true),
            ) shouldBe outcome

            verify(mockPhoneMessageService).updateSeen(
                check {
                    it.ids shouldBe listOf("a")
                    it.seen shouldBe true
                },
            )
        }

    @Test
    fun `UpdatePhoneMessagesUseCase execute() should propagate a domain exception unchanged`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { updateSeen(any()) } doThrow PhoneMessageException.FailedException("boom")
            }

            shouldThrow<PhoneMessageException.FailedException> {
                updateUseCase.execute(UpdatePhoneMessagesUseCaseInput(ids = listOf("a"), seen = false))
            }
        }
}
