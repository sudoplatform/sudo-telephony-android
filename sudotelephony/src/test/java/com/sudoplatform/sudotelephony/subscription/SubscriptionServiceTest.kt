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
import com.sudoplatform.sudotelephony.graphql.OnPhoneNumberUpdateSubscription
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
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

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
    fun `a phone number subscription that fails to establish should report DISCONNECTED`() =
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
            verify(subscriber).connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }

    @Test
    fun `a phone message subscription that fails to establish should report DISCONNECTED`() =
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

            verify(subscriber).connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }

    @Test
    fun `an established phone number subscription should report CONNECTED`() =
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

            verify(subscriber).connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)
        }

    @Test
    fun `a completed phone number subscription should report DISCONNECTED`() =
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

            verify(subscriber).connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }
}
