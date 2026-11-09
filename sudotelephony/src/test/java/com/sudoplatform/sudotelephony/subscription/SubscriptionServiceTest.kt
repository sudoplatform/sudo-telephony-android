/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.subscription

import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Action
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.data.DataFactory
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.graphql.OnPhoneMessageEventSubscription
import com.sudoplatform.sudotelephony.graphql.OnPhoneNumberUpdateSubscription
import com.sudoplatform.sudotelephony.types.PhoneMessageChangeType
import com.sudoplatform.sudotelephony.types.PhoneMessageDirection
import com.sudoplatform.sudotelephony.types.PhoneMessageEvent
import com.sudoplatform.sudotelephony.types.PhoneMessageType
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberFailureReason
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import com.sudoplatform.sudotelephony.graphql.fragment.PhoneNumber as PhoneNumberFragment
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageChangeType as PhoneMessageChangeTypeGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageDirection as PhoneMessageDirectionGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageType as PhoneMessageTypeGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberFailureReason as PhoneNumberFailureReasonGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberState as PhoneNumberStateGraphQL

/**
 * Test the correct operation of [SubscriptionService]: the owner is resolved before
 * subscribing, and a subscription that fails to establish is reported to its subscribers
 * rather than leaving them waiting.
 */
@RunWith(RobolectricTestRunner::class)
class SubscriptionServiceTest : BaseTests() {
    override val mockApiClient by before { mock<ApiClient>() }

    private val instanceUnderTest by before {
        SubscriptionService(mockApiClient, mockUserClient, mockLogger)
    }

    @Test
    fun `subscribeToPhoneNumbers() should reject an unauthenticated caller`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn null }

            shouldThrow<SudoTelephonyClient.PhoneNumberException.AuthenticationException> {
                instanceUnderTest.subscribeToPhoneNumbers("sub-1", mock())
            }
        }

    @Test
    fun `subscribeToPhoneMessages() should reject an unauthenticated caller`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn null }

            shouldThrow<SudoTelephonyClient.PhoneMessageException.AuthenticationException> {
                instanceUnderTest.subscribeToPhoneMessages("sub-1", mock())
            }
        }

    @Test
    fun `subscribeToPhoneNumbers() should subscribe for the signed-in owner`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn "mockOwner" }

            instanceUnderTest.subscribeToPhoneNumbers("sub-1", mock())

            verify(mockApiClient).onPhoneNumberUpdateSubscription(
                eq("mockOwner"),
                any(),
                any(),
                any(),
                any(),
            )
        }

    @Test
    fun `a phone number subscription that fails to establish should report Disconnected with a cause`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn "mockOwner" }
            val subscriber = mock<PhoneNumberSubscriber>()
            val onFailure = argumentCaptor<Consumer<ApiException>>()

            instanceUnderTest.subscribeToPhoneNumbers("sub-1", subscriber)

            verify(mockApiClient).onPhoneNumberUpdateSubscription(
                any(),
                any(),
                any(),
                any(),
                onFailure.capture(),
            )

            // Amplify reports the subscription could not be established or was lost.
            onFailure.firstValue.accept(ApiException("boom", "try again"))

            // Without this the caller would wait forever for a connection notification.
            val state = argumentCaptor<Subscriber.ConnectionState>()
            verify(subscriber).connectionStatusChanged(state.capture())
            val cause = (state.firstValue as Subscriber.ConnectionState.Disconnected).cause
            cause.shouldBeInstanceOf<SudoTelephonyClient.PhoneNumberException.FailedException>()
            cause?.cause.shouldBeInstanceOf<ApiException>()
        }

    @Test
    fun `a phone message subscription that fails to establish should report Disconnected with a cause`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn "mockOwner" }
            val subscriber = mock<PhoneMessageSubscriber>()
            val onFailure = argumentCaptor<Consumer<ApiException>>()

            instanceUnderTest.subscribeToPhoneMessages("sub-1", subscriber)

            verify(mockApiClient).onPhoneMessageEventSubscription(
                any(),
                any(),
                any(),
                any(),
                onFailure.capture(),
            )

            onFailure.firstValue.accept(ApiException("boom", "try again"))

            val state = argumentCaptor<Subscriber.ConnectionState>()
            verify(subscriber).connectionStatusChanged(state.capture())
            val cause = (state.firstValue as Subscriber.ConnectionState.Disconnected).cause
            cause.shouldBeInstanceOf<SudoTelephonyClient.PhoneMessageException.FailedException>()
            cause?.cause.shouldBeInstanceOf<ApiException>()
        }

    @Test
    fun `a phone number subscription that is not authorized should report an AuthenticationException`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn "mockOwner" }
            val subscriber = mock<PhoneNumberSubscriber>()
            val onFailure = argumentCaptor<Consumer<ApiException>>()

            instanceUnderTest.subscribeToPhoneNumbers("sub-1", subscriber)

            verify(mockApiClient).onPhoneNumberUpdateSubscription(any(), any(), any(), any(), onFailure.capture())

            onFailure.firstValue.accept(ApiException.ApiAuthException("unauthorized", "sign in"))

            val state = argumentCaptor<Subscriber.ConnectionState>()
            verify(subscriber).connectionStatusChanged(state.capture())
            val cause = (state.firstValue as Subscriber.ConnectionState.Disconnected).cause
            cause.shouldBeInstanceOf<SudoTelephonyClient.PhoneNumberException.AuthenticationException>()
            cause?.cause.shouldBeInstanceOf<ApiException.ApiAuthException>()
        }

    @Test
    fun `a phone message subscription that is not authorized should report an AuthenticationException`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn "mockOwner" }
            val subscriber = mock<PhoneMessageSubscriber>()
            val onFailure = argumentCaptor<Consumer<ApiException>>()

            instanceUnderTest.subscribeToPhoneMessages("sub-1", subscriber)

            verify(mockApiClient).onPhoneMessageEventSubscription(any(), any(), any(), any(), onFailure.capture())

            onFailure.firstValue.accept(ApiException.ApiAuthException("unauthorized", "sign in"))

            val state = argumentCaptor<Subscriber.ConnectionState>()
            verify(subscriber).connectionStatusChanged(state.capture())
            val cause = (state.firstValue as Subscriber.ConnectionState.Disconnected).cause
            cause.shouldBeInstanceOf<SudoTelephonyClient.PhoneMessageException.AuthenticationException>()
            cause?.cause.shouldBeInstanceOf<ApiException.ApiAuthException>()
        }

    @Test
    fun `an established phone number subscription should report Connected`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn "mockOwner" }
            val subscriber = mock<PhoneNumberSubscriber>()
            val onEstablished =
                argumentCaptor<Consumer<GraphQLResponse<OnPhoneNumberUpdateSubscription.Data>>>()

            instanceUnderTest.subscribeToPhoneNumbers("sub-1", subscriber)

            verify(mockApiClient).onPhoneNumberUpdateSubscription(
                any(),
                onEstablished.capture(),
                any(),
                any(),
                any(),
            )

            onEstablished.firstValue.accept(GraphQLResponse(null, null))

            verify(subscriber).connectionStatusChanged(Subscriber.ConnectionState.Connected)
        }

    @Test
    fun `a phone number update should deliver OWNER_DELETED and unrecognised values`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn "mockOwner" }
            val subscriber = mock<PhoneNumberSubscriber>()
            val onSubscription =
                argumentCaptor<Consumer<GraphQLResponse<OnPhoneNumberUpdateSubscription.Data>>>()

            instanceUnderTest.subscribeToPhoneNumbers("sub-1", subscriber)

            verify(mockApiClient).onPhoneNumberUpdateSubscription(
                any(),
                any(),
                onSubscription.capture(),
                any(),
                any(),
            )

            fun deliver(number: PhoneNumberFragment) =
                onSubscription.firstValue.accept(
                    GraphQLResponse(
                        OnPhoneNumberUpdateSubscription.Data(
                            OnPhoneNumberUpdateSubscription.OnPhoneNumberUpdate("PhoneNumber", number),
                        ),
                        null,
                    ),
                )
            deliver(
                DataFactory.phoneNumberFragment(
                    state = PhoneNumberStateGraphQL.FAILED,
                    failureReason = PhoneNumberFailureReasonGraphQL.OWNER_DELETED,
                ),
            )
            deliver(DataFactory.phoneNumberFragment(state = PhoneNumberStateGraphQL.safeValueOf("NEW_STATE")))

            val updates = argumentCaptor<PhoneNumber>()
            verify(subscriber, timeout(1_000).times(2)).phoneNumberUpdated(updates.capture())
            updates.allValues.map { it.failureReason } shouldContain PhoneNumberFailureReason.OwnerDeleted
            updates.allValues.map { it.state } shouldContain PhoneNumberState.Unknown("NEW_STATE")
        }

    @Test
    fun `a phone message event should deliver unrecognised values`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn "mockOwner" }
            val subscriber = mock<PhoneMessageSubscriber>()
            val onSubscription =
                argumentCaptor<Consumer<GraphQLResponse<OnPhoneMessageEventSubscription.Data>>>()

            instanceUnderTest.subscribeToPhoneMessages("sub-1", subscriber)

            verify(mockApiClient).onPhoneMessageEventSubscription(
                any(),
                any(),
                onSubscription.capture(),
                any(),
                any(),
            )

            onSubscription.firstValue.accept(
                GraphQLResponse(
                    OnPhoneMessageEventSubscription.Data(
                        OnPhoneMessageEventSubscription.OnPhoneMessageEvent(
                            id = "mockMessageId",
                            phoneNumberId = "mockPhoneNumberId",
                            conversationId = "mockConversationId",
                            type = PhoneMessageTypeGraphQL.safeValueOf("MMS"),
                            direction = PhoneMessageDirectionGraphQL.safeValueOf("OUTBOUND"),
                            changeType = PhoneMessageChangeTypeGraphQL.safeValueOf("ARCHIVED"),
                            seen = false,
                            owner = "mockOwner",
                            owners = emptyList(),
                            version = 1,
                            updatedAtEpochMs = EntityDataFactory.UPDATED_AT_EPOCH_MS,
                        ),
                    ),
                    null,
                ),
            )

            val event = argumentCaptor<PhoneMessageEvent>()
            verify(subscriber, timeout(1_000)).phoneMessageEvent(event.capture())
            event.firstValue.type shouldBe PhoneMessageType.Unknown("MMS")
            event.firstValue.direction shouldBe PhoneMessageDirection.Unknown("OUTBOUND")
            event.firstValue.changeType shouldBe PhoneMessageChangeType.Unknown("ARCHIVED")
        }

    @Test
    fun `a completed phone number subscription should report Disconnected without a cause`() =
        runTest {
            mockUserClient.stub { on { getSubject() } doReturn "mockOwner" }
            val subscriber = mock<PhoneNumberSubscriber>()
            val onCompleted = argumentCaptor<Action>()

            instanceUnderTest.subscribeToPhoneNumbers("sub-1", subscriber)

            verify(mockApiClient).onPhoneNumberUpdateSubscription(
                any(),
                any(),
                any(),
                onCompleted.capture(),
                any(),
            )

            onCompleted.firstValue.call()

            // A subscription that ends normally has no cause.
            verify(subscriber).connectionStatusChanged(Subscriber.ConnectionState.Disconnected(null))
        }
}
