/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.data.DataFactory
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageDirectionEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageTypeEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SortOrderEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.UpdatePhoneMessagesInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
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
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import com.sudoplatform.sudotelephony.graphql.type.SortOrder as SortOrderGraphQL

/**
 * Test the correct operation of [GraphQLPhoneMessageService]: the right query is executed and
 * the response is mapped to a sealed entity with its sealed fields left untouched.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLPhoneMessageServiceTest : BaseTests() {
    override val mockApiClient by before { mock<ApiClient>() }

    private val instanceUnderTest by before {
        GraphQLPhoneMessageService(mockApiClient, mockLogger)
    }

    private val listInput = ListPhoneMessagesInput(phoneNumberId = "mockPhoneNumberId")

    @After
    fun fini() {
        verifyNoMoreInteractions(mockApiClient)
    }

    @Test
    fun `get() should return the sealed entity with its sealed fields untouched`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getPhoneMessageQuery(any()) } doReturn
                    DataFactory.getPhoneMessageQueryResponse()
            }

            val result = instanceUnderTest.get("mockMessageId")

            result shouldNotBe null
            result!!.id shouldBe "mockMessageId"
            result.type shouldBe PhoneMessageTypeEntity.SMS
            result.direction shouldBe PhoneMessageDirectionEntity.INBOUND
            result.localPhoneNumber shouldBe "+14155550123"
            // The data layer performs no decryption.
            result.body shouldBe EntityDataFactory.SEALED_BODY
            result.remotePhoneNumber shouldBe EntityDataFactory.SEALED_REMOTE_NUMBER

            verify(mockApiClient).getPhoneMessageQuery(eq("mockMessageId"))
        }

    @Test
    fun `get() should return null when no message with the id belongs to the caller`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getPhoneMessageQuery(any()) } doReturn
                    DataFactory.getPhoneMessageQueryResponse(message = null)
            }

            instanceUnderTest.get("mockMessageId") shouldBe null

            verify(mockApiClient).getPhoneMessageQuery(any())
        }

    @Test
    fun `get() should throw InvalidArgumentException on a bad argument`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getPhoneMessageQuery(any()) } doReturn
                    DataFactory.getPhoneMessageErrorResponse(
                        DataFactory.graphQLError(
                            extensions = mapOf(StringConstants.ERROR_TYPE to "sudoplatform.InvalidArgumentError"),
                        ),
                    )
            }

            shouldThrow<PhoneMessageException.InvalidArgumentException> {
                instanceUnderTest.get("mockMessageId")
            }

            verify(mockApiClient).getPhoneMessageQuery(any())
        }

    @Test
    fun `get() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getPhoneMessageQuery(any()) } doThrow RuntimeException("network down")
            }

            shouldThrow<PhoneMessageException.UnknownException> {
                instanceUnderTest.get("mockMessageId")
            }

            verify(mockApiClient).getPhoneMessageQuery(any())
        }

    @Test
    fun `list() should return the mapped page and its next token`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneMessagesQuery(any()) } doReturn
                    DataFactory.listPhoneMessagesQueryResponse(
                        messages =
                            listOf(
                                DataFactory.sealedPhoneMessageFragment(id = "first"),
                                DataFactory.sealedPhoneMessageFragment(id = "second"),
                            ),
                        nextToken = "mockNextToken",
                    )
            }

            val result = instanceUnderTest.list(listInput)

            result.items.map { it.id } shouldBe listOf("first", "second")
            result.nextToken shouldBe "mockNextToken"

            verify(mockApiClient).listPhoneMessagesQuery(any())
        }

    @Test
    fun `list() should return an empty page`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneMessagesQuery(any()) } doReturn
                    DataFactory.listPhoneMessagesQueryResponse(messages = emptyList())
            }

            val result = instanceUnderTest.list(listInput)

            result.items shouldBe emptyList()
            result.nextToken shouldBe null

            verify(mockApiClient).listPhoneMessagesQuery(any())
        }

    @Test
    fun `list() should always send the phone number and omit unset filters`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneMessagesQuery(any()) } doReturn
                    DataFactory.listPhoneMessagesQueryResponse()
            }

            instanceUnderTest.list(listInput)

            verify(mockApiClient).listPhoneMessagesQuery(
                check {
                    it.phoneNumberId shouldBe "mockPhoneNumberId"
                    it.conversationId.getOrNull() shouldBe null
                    it.sortOrder.getOrNull() shouldBe null
                    it.limit.getOrNull() shouldBe null
                    it.nextToken.getOrNull() shouldBe null
                },
            )
        }

    @Test
    fun `list() should send every filter the caller set`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneMessagesQuery(any()) } doReturn
                    DataFactory.listPhoneMessagesQueryResponse()
            }

            instanceUnderTest.list(
                ListPhoneMessagesInput(
                    phoneNumberId = "mockPhoneNumberId",
                    conversationId = "mockConversationId",
                    sortOrder = SortOrderEntity.ASC,
                    limit = 9,
                    nextToken = "token",
                ),
            )

            verify(mockApiClient).listPhoneMessagesQuery(
                check {
                    it.conversationId.getOrNull() shouldBe "mockConversationId"
                    it.sortOrder.getOrNull() shouldBe SortOrderGraphQL.ASC
                    it.limit.getOrNull() shouldBe 9
                    it.nextToken.getOrNull() shouldBe "token"
                },
            )
        }

    @Test
    fun `list() should throw FailedException when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneMessagesQuery(any()) } doReturn
                    DataFactory.listPhoneMessagesEmptyResponse()
            }

            shouldThrow<PhoneMessageException.FailedException> {
                instanceUnderTest.list(listInput)
            }

            verify(mockApiClient).listPhoneMessagesQuery(any())
        }

    @Test
    fun `list() should throw PhoneNumberNotFoundException for an unknown number`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneMessagesQuery(any()) } doReturn
                    DataFactory.listPhoneMessagesErrorResponse(
                        DataFactory.graphQLError(
                            extensions =
                                mapOf(
                                    StringConstants.ERROR_TYPE to "sudoplatform.telephony.PhoneNumberNotFound",
                                ),
                        ),
                    )
            }

            shouldThrow<PhoneMessageException.PhoneNumberNotFoundException> {
                instanceUnderTest.list(listInput)
            }

            verify(mockApiClient).listPhoneMessagesQuery(any())
        }

    @Test
    fun `list() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneMessagesQuery(any()) } doThrow RuntimeException("network down")
            }

            shouldThrow<PhoneMessageException.UnknownException> {
                instanceUnderTest.list(listInput)
            }

            verify(mockApiClient).listPhoneMessagesQuery(any())
        }

    @Test
    fun `delete() should return the batch outcome the service reported`() =
        runTest {
            mockApiClient.stub {
                onBlocking { deletePhoneMessagesMutation(any()) } doReturn
                    DataFactory.deletePhoneMessagesMutationResponse(
                        succeededIds = listOf("deleted"),
                        failedIds = listOf("missing"),
                    )
            }

            val result = instanceUnderTest.delete(DeletePhoneMessagesInput(ids = listOf("deleted", "missing")))

            result.succeededIds shouldBe listOf("deleted")
            result.failedIds shouldBe listOf("missing")

            verify(mockApiClient).deletePhoneMessagesMutation(check { it.ids shouldBe listOf("deleted", "missing") })
        }

    @Test
    fun `delete() should throw LimitExceededException for an over-sized batch`() =
        runTest {
            mockApiClient.stub {
                onBlocking { deletePhoneMessagesMutation(any()) } doReturn
                    DataFactory.deletePhoneMessagesErrorResponse(
                        DataFactory.graphQLError(
                            extensions = mapOf(StringConstants.ERROR_TYPE to "sudoplatform.LimitExceededError"),
                        ),
                    )
            }

            shouldThrow<PhoneMessageException.LimitExceededException> {
                instanceUnderTest.delete(DeletePhoneMessagesInput(ids = listOf("a")))
            }

            verify(mockApiClient).deletePhoneMessagesMutation(any())
        }

    @Test
    fun `delete() should throw FailedException when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking { deletePhoneMessagesMutation(any()) } doReturn
                    DataFactory.deletePhoneMessagesEmptyResponse()
            }

            shouldThrow<PhoneMessageException.FailedException> {
                instanceUnderTest.delete(DeletePhoneMessagesInput(ids = listOf("a")))
            }

            verify(mockApiClient).deletePhoneMessagesMutation(any())
        }

    @Test
    fun `delete() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { deletePhoneMessagesMutation(any()) } doThrow RuntimeException("network down")
            }

            shouldThrow<PhoneMessageException.UnknownException> {
                instanceUnderTest.delete(DeletePhoneMessagesInput(ids = listOf("a")))
            }

            verify(mockApiClient).deletePhoneMessagesMutation(any())
        }

    @Test
    fun `updateSeen() should send the ids and the requested read state`() =
        runTest {
            mockApiClient.stub {
                onBlocking { updatePhoneMessagesMutation(any()) } doReturn
                    DataFactory.updatePhoneMessagesMutationResponse()
            }

            instanceUnderTest.updateSeen(UpdatePhoneMessagesInput(ids = listOf("a", "b"), seen = true))

            verify(mockApiClient).updatePhoneMessagesMutation(
                check {
                    it.ids shouldBe listOf("a", "b")
                    it.seen shouldBe true
                },
            )
        }

    @Test
    fun `updateSeen() should send a false read state unchanged`() =
        runTest {
            mockApiClient.stub {
                onBlocking { updatePhoneMessagesMutation(any()) } doReturn
                    DataFactory.updatePhoneMessagesMutationResponse()
            }

            instanceUnderTest.updateSeen(UpdatePhoneMessagesInput(ids = listOf("a"), seen = false))

            verify(mockApiClient).updatePhoneMessagesMutation(check { it.seen shouldBe false })
        }

    @Test
    fun `updateSeen() should return the batch outcome the service reported`() =
        runTest {
            mockApiClient.stub {
                onBlocking { updatePhoneMessagesMutation(any()) } doReturn
                    DataFactory.updatePhoneMessagesMutationResponse(
                        succeededIds = emptyList(),
                        failedIds = listOf("missing"),
                    )
            }

            val result = instanceUnderTest.updateSeen(UpdatePhoneMessagesInput(ids = listOf("missing"), seen = true))

            result.succeededIds shouldBe emptyList()
            result.failedIds shouldBe listOf("missing")

            verify(mockApiClient).updatePhoneMessagesMutation(any())
        }

    @Test
    fun `updateSeen() should throw LimitExceededException for an over-sized batch`() =
        runTest {
            mockApiClient.stub {
                onBlocking { updatePhoneMessagesMutation(any()) } doReturn
                    DataFactory.updatePhoneMessagesErrorResponse(
                        DataFactory.graphQLError(
                            extensions = mapOf(StringConstants.ERROR_TYPE to "sudoplatform.LimitExceededError"),
                        ),
                    )
            }

            shouldThrow<PhoneMessageException.LimitExceededException> {
                instanceUnderTest.updateSeen(UpdatePhoneMessagesInput(ids = listOf("a"), seen = true))
            }

            verify(mockApiClient).updatePhoneMessagesMutation(any())
        }
}
