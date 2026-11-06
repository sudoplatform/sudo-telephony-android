/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.common.ListPartialResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.common.ListSuccessResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.common.PartialResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesForConversationIdInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesForPhoneNumberIdInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesOutput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageBatchResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SortOrderEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.UnsealedPhoneMessages
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
                onBlocking { unseal(any(), any()) } doReturn UnsealedPhoneMessages(listOf(unsealed), emptyList())
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
    fun `GetPhoneMessageUseCase execute() should propagate a missing key`() =
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
    fun `GetPhoneMessageUseCase execute() should throw the cause when the message cannot be unsealed`() =
        runTest {
            val unsealingFailure = PhoneMessageException.UnsealingFailedException("bad")
            mockPhoneMessageService.stub {
                onBlocking { get(any()) } doReturn EntityDataFactory.sealedPhoneMessageEntity()
            }
            mockUnsealer.stub {
                onBlocking {
                    unseal(any(), any())
                } doReturn
                    UnsealedPhoneMessages(
                        unsealed = emptyList(),
                        failed =
                            listOf(
                                PartialResultEntity(
                                    EntityDataFactory.partialPhoneMessageEntity(),
                                    unsealingFailure,
                                ),
                            ),
                    )
            }

            val thrown =
                shouldThrow<PhoneMessageException.UnsealingFailedException> {
                    getUseCase.execute("mockMessageId")
                }
            thrown shouldBe unsealingFailure
        }

    @Test
    fun `ListPhoneMessagesUseCase execute(phoneNumberId) should return the unsealed page and next token`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { listForPhoneNumberId(any()) } doReturn
                    ListPhoneMessagesOutput(
                        items = listOf(EntityDataFactory.sealedPhoneMessageEntity()),
                        nextToken = "mockNextToken",
                    )
            }
            mockUnsealer.stub {
                onBlocking {
                    unseal(any(), any())
                } doReturn
                    UnsealedPhoneMessages(
                        unsealed = listOf(EntityDataFactory.phoneMessageEntity(body = "Hello there")),
                        failed = emptyList(),
                    )
            }

            val result =
                listUseCase.execute(
                    ListPhoneMessagesForPhoneNumberIdInput(phoneNumberId = "mockPhoneNumberId"),
                )

            result shouldBe
                ListAPIResultEntity.Success(
                    ListSuccessResultEntity(
                        items = listOf(EntityDataFactory.phoneMessageEntity(body = "Hello there")),
                        nextToken = "mockNextToken",
                    ),
                )
        }

    @Test
    fun `ListPhoneMessagesUseCase execute(phoneNumberId) should map every filter onto the service input`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { listForPhoneNumberId(any()) } doReturn ListPhoneMessagesOutput(items = emptyList())
            }
            mockUnsealer.stub {
                onBlocking { unseal(any(), any()) } doReturn UnsealedPhoneMessages(emptyList(), emptyList())
            }

            listUseCase.execute(
                ListPhoneMessagesForPhoneNumberIdInput(
                    phoneNumberId = "mockPhoneNumberId",
                    sortOrder = SortOrderEntity.ASC,
                    limit = 4,
                    nextToken = "token",
                ),
            )

            verify(mockPhoneMessageService).listForPhoneNumberId(
                check {
                    it.phoneNumberId shouldBe "mockPhoneNumberId"
                    it.sortOrder shouldBe SortOrderEntity.ASC
                    it.limit shouldBe 4
                    it.nextToken shouldBe "token"
                },
            )
        }

    @Test
    fun `ListPhoneMessagesUseCase execute(phoneNumberId) should return an empty page unchanged`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { listForPhoneNumberId(any()) } doReturn ListPhoneMessagesOutput(items = emptyList())
            }
            mockUnsealer.stub {
                onBlocking { unseal(any(), any()) } doReturn UnsealedPhoneMessages(emptyList(), emptyList())
            }

            val result =
                listUseCase.execute(
                    ListPhoneMessagesForPhoneNumberIdInput(phoneNumberId = "mockPhoneNumberId"),
                )

            result shouldBe ListAPIResultEntity.Success(ListSuccessResultEntity(emptyList(), null))
        }

    @Test
    fun `ListPhoneMessagesUseCase execute(phoneNumberId) should return a partial page when a message cannot be unsealed`() =
        runTest {
            val unsealed = EntityDataFactory.phoneMessageEntity(id = "readable")
            val failed =
                PartialResultEntity(
                    EntityDataFactory.partialPhoneMessageEntity(id = "unreadable"),
                    PhoneMessageException.UnsealingFailedException("bad"),
                )
            mockPhoneMessageService.stub {
                onBlocking { listForPhoneNumberId(any()) } doReturn
                    ListPhoneMessagesOutput(
                        items =
                            listOf(
                                EntityDataFactory.sealedPhoneMessageEntity(id = "readable"),
                                EntityDataFactory.sealedPhoneMessageEntity(id = "unreadable"),
                            ),
                        nextToken = "mockNextToken",
                    )
            }
            mockUnsealer.stub {
                onBlocking {
                    unseal(any(), any())
                } doReturn UnsealedPhoneMessages(listOf(unsealed), listOf(failed))
            }

            val result =
                listUseCase.execute(
                    ListPhoneMessagesForPhoneNumberIdInput(phoneNumberId = "mockPhoneNumberId"),
                )

            result shouldBe
                ListAPIResultEntity.Partial(
                    ListPartialResultEntity(
                        items = listOf(unsealed),
                        failed = listOf(failed),
                        nextToken = "mockNextToken",
                    ),
                )
        }

    @Test
    fun `ListPhoneMessagesUseCase execute(phoneNumberId) should propagate a domain exception unchanged`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking {
                    listForPhoneNumberId(any())
                } doThrow PhoneMessageException.PhoneNumberNotFoundException("gone")
            }

            shouldThrow<PhoneMessageException.PhoneNumberNotFoundException> {
                listUseCase.execute(
                    ListPhoneMessagesForPhoneNumberIdInput(phoneNumberId = "mockPhoneNumberId"),
                )
            }
        }

    @Test
    fun `ListPhoneMessagesUseCase execute(conversationId) should unseal with the phone number of the page's messages`() =
        runTest {
            val input =
                ListPhoneMessagesForConversationIdInput(
                    conversationId = "mockConversationId",
                    sortOrder = SortOrderEntity.ASC,
                    limit = 4,
                    nextToken = "token",
                )
            mockPhoneMessageService.stub {
                onBlocking { listForConversationId(any()) } doReturn
                    ListPhoneMessagesOutput(
                        items = listOf(EntityDataFactory.sealedPhoneMessageEntity(phoneNumberId = "conversationNumberId")),
                        nextToken = "mockNextToken",
                    )
            }
            mockUnsealer.stub {
                onBlocking {
                    unseal(any(), any())
                } doReturn UnsealedPhoneMessages(listOf(EntityDataFactory.phoneMessageEntity()), emptyList())
            }

            val result = listUseCase.execute(input)

            result shouldBe
                ListAPIResultEntity.Success(
                    ListSuccessResultEntity(
                        items = listOf(EntityDataFactory.phoneMessageEntity()),
                        nextToken = "mockNextToken",
                    ),
                )
            verify(mockPhoneMessageService).listForConversationId(eq(input))
            verify(mockUnsealer).unseal(eq("conversationNumberId"), any())
        }

    @Test
    fun `ListPhoneMessagesUseCase execute(conversationId) should return an empty page without unsealing`() =
        runTest {
            mockPhoneMessageService.stub {
                onBlocking { listForConversationId(any()) } doReturn
                    ListPhoneMessagesOutput(items = emptyList(), nextToken = "mockNextToken")
            }

            val result =
                listUseCase.execute(
                    ListPhoneMessagesForConversationIdInput(conversationId = "mockConversationId"),
                )

            result shouldBe ListAPIResultEntity.Success(ListSuccessResultEntity(emptyList(), "mockNextToken"))
            verifyNoInteractions(mockUnsealer)
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
