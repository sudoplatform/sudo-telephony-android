/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageBatchResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SortOrderEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.DeletePhoneMessagesUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.GetPhoneMessageUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.ListPhoneMessagesUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.ListPhoneMessagesUseCaseOutput
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.UpdatePhoneMessagesUseCase
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.types.PhoneMessageDirection
import com.sudoplatform.sudotelephony.types.PhoneMessageType
import com.sudoplatform.sudotelephony.types.SortOrder
import com.sudoplatform.sudotelephony.types.inputs.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.UpdatePhoneMessagesInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoTelephonyClient.getPhoneMessage] and
 * [SudoTelephonyClient.listPhoneMessages] using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonyPhoneMessageTest : BaseTests() {
    private val mockCountryService by before { mock<CountryService>() }
    private val mockPhoneNumberSearchService by before { mock<PhoneNumberSearchService>() }
    private val mockPhoneNumberService by before { mock<PhoneNumberService>() }
    private val mockPhoneMessageService by before { mock<PhoneMessageService>() }
    private val mockPhoneMessageUnsealer by before { mock<PhoneMessageUnsealer>() }
    private val mockServiceKeyManager by before { mock<ServiceKeyManager>() }

    private val mockGetUseCase by before {
        mock<GetPhoneMessageUseCase>().stub {
            onBlocking { execute(any()) } doReturn EntityDataFactory.phoneMessageEntity()
        }
    }

    private val mockListUseCase by before {
        mock<ListPhoneMessagesUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn
                ListPhoneMessagesUseCaseOutput(
                    items = listOf(EntityDataFactory.phoneMessageEntity()),
                    nextToken = "mockNextToken",
                )
        }
    }

    private val mockDeleteUseCase by before {
        mock<DeletePhoneMessagesUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn
                PhoneMessageBatchResultEntity(
                    succeededIds = listOf("deleted"),
                    failedIds = listOf("missing"),
                )
        }
    }

    private val mockUpdateUseCase by before {
        mock<UpdatePhoneMessagesUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn
                PhoneMessageBatchResultEntity(
                    succeededIds = listOf("updated"),
                    failedIds = emptyList(),
                )
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetPhoneMessageUseCase() } doReturn mockGetUseCase
            on { createListPhoneMessagesUseCase() } doReturn mockListUseCase
            on { createDeletePhoneMessagesUseCase() } doReturn mockDeleteUseCase
            on { createUpdatePhoneMessagesUseCase() } doReturn mockUpdateUseCase
        }
    }

    private val client by before {
        DefaultSudoTelephonyClient(
            context = mockContext,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            countryService = mockCountryService,
            phoneNumberSearchService = mockPhoneNumberSearchService,
            phoneNumberService = mockPhoneNumberService,
            phoneMessageService = mockPhoneMessageService,
            phoneMessageUnsealer = mockPhoneMessageUnsealer,
            serviceKeyManager = mockServiceKeyManager,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockApiClient,
            mockServiceKeyManager,
            mockUseCaseFactory,
            mockGetUseCase,
            mockListUseCase,
            mockDeleteUseCase,
            mockUpdateUseCase,
        )
    }

    @Test
    fun `getPhoneMessage() should return the readable message`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getPhoneMessage("mockMessageId")
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            with(result!!) {
                id shouldBe "mockMessageId"
                phoneNumberId shouldBe "mockPhoneNumberId"
                type shouldBe PhoneMessageType.SMS
                direction shouldBe PhoneMessageDirection.INBOUND
                body shouldBe "Hello there"
                remotePhoneNumber shouldBe "+14155559876"
                localPhoneNumber shouldBe "+14155550123"
                seen shouldBe false
            }

            verify(mockUseCaseFactory).createGetPhoneMessageUseCase()
            verify(mockGetUseCase).execute(check { it shouldBe "mockMessageId" })
        }

    @Test
    fun `getPhoneMessage() should return null when the message is absent`() =
        runTest {
            mockGetUseCase.stub {
                onBlocking { execute(any()) } doReturn null
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getPhoneMessage("mockMessageId")
                }
            deferredResult.start()

            deferredResult.await() shouldBe null

            verify(mockUseCaseFactory).createGetPhoneMessageUseCase()
            verify(mockGetUseCase).execute(any())
        }

    @Test
    fun `getPhoneMessage() should throw when the message cannot be unsealed`() =
        runTest {
            mockGetUseCase.stub {
                onBlocking { execute(any()) } doThrow PhoneMessageException.KeyNotFoundException()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<PhoneMessageException.KeyNotFoundException> {
                        client.getPhoneMessage("mockMessageId")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createGetPhoneMessageUseCase()
            verify(mockGetUseCase).execute(any())
        }

    @Test
    fun `listPhoneMessages() should return the readable page and next token`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listPhoneMessages(
                        ListPhoneMessagesInput(phoneNumberId = "mockPhoneNumberId"),
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result.items.size shouldBe 1
            result.items.first().body shouldBe "Hello there"
            result.nextToken shouldBe "mockNextToken"

            verify(mockUseCaseFactory).createListPhoneMessagesUseCase()
            verify(mockListUseCase).execute(any())
        }

    @Test
    fun `listPhoneMessages() should pass every filter to the use case`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listPhoneMessages(
                        ListPhoneMessagesInput(
                            phoneNumberId = "mockPhoneNumberId",
                            conversationId = "mockConversationId",
                            sortOrder = SortOrder.ASC,
                            limit = 5,
                            nextToken = "token",
                        ),
                    )
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListPhoneMessagesUseCase()
            verify(mockListUseCase).execute(
                check {
                    it.phoneNumberId shouldBe "mockPhoneNumberId"
                    it.conversationId shouldBe "mockConversationId"
                    it.sortOrder shouldBe SortOrderEntity.ASC
                    it.limit shouldBe 5
                    it.nextToken shouldBe "token"
                },
            )
        }

    @Test
    fun `listPhoneMessages() should return an empty page`() =
        runTest {
            mockListUseCase.stub {
                onBlocking { execute(any()) } doReturn ListPhoneMessagesUseCaseOutput(items = emptyList())
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listPhoneMessages(
                        ListPhoneMessagesInput(phoneNumberId = "mockPhoneNumberId"),
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result.items shouldBe emptyList()
            result.nextToken shouldBe null

            verify(mockUseCaseFactory).createListPhoneMessagesUseCase()
            verify(mockListUseCase).execute(any())
        }

    @Test
    fun `listPhoneMessages() should throw when the use case throws`() =
        runTest {
            mockListUseCase.stub {
                onBlocking {
                    execute(any())
                } doThrow PhoneMessageException.PhoneNumberNotFoundException()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<PhoneMessageException.PhoneNumberNotFoundException> {
                        client.listPhoneMessages(
                            ListPhoneMessagesInput(phoneNumberId = "mockPhoneNumberId"),
                        )
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListPhoneMessagesUseCase()
            verify(mockListUseCase).execute(any())
        }

    @Test
    fun `deletePhoneMessages() should return the batch outcome`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deletePhoneMessages(
                        DeletePhoneMessagesInput(ids = listOf("deleted", "missing")),
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            // A partial success is reported rather than thrown.
            result.succeededIds shouldBe listOf("deleted")
            result.failedIds shouldBe listOf("missing")

            verify(mockUseCaseFactory).createDeletePhoneMessagesUseCase()
            verify(mockDeleteUseCase).execute(check { it shouldBe listOf("deleted", "missing") })
        }

    @Test
    fun `deletePhoneMessages() should throw when the use case throws`() =
        runTest {
            mockDeleteUseCase.stub {
                onBlocking { execute(any()) } doThrow PhoneMessageException.LimitExceededException()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<PhoneMessageException.LimitExceededException> {
                        client.deletePhoneMessages(DeletePhoneMessagesInput(ids = listOf("a")))
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createDeletePhoneMessagesUseCase()
            verify(mockDeleteUseCase).execute(any())
        }

    @Test
    fun `updatePhoneMessages() should pass the ids and read state and return the outcome`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updatePhoneMessages(
                        UpdatePhoneMessagesInput(ids = listOf("updated"), seen = true),
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result.succeededIds shouldBe listOf("updated")
            result.failedIds shouldBe emptyList()

            verify(mockUseCaseFactory).createUpdatePhoneMessagesUseCase()
            verify(mockUpdateUseCase).execute(
                check {
                    it.ids shouldBe listOf("updated")
                    it.seen shouldBe true
                },
            )
        }

    @Test
    fun `updatePhoneMessages() should pass a false read state unchanged`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updatePhoneMessages(
                        UpdatePhoneMessagesInput(ids = listOf("updated"), seen = false),
                    )
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createUpdatePhoneMessagesUseCase()
            verify(mockUpdateUseCase).execute(check { it.seen shouldBe false })
        }
}
