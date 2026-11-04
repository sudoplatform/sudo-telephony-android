/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.subscription.PhoneMessageSubscriber
import com.sudoplatform.sudotelephony.subscription.PhoneNumberSubscriber
import com.sudoplatform.sudotelephony.subscription.SubscriptionService
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

/**
 * Test that the client's subscription methods delegate to the [SubscriptionService], and that
 * closing or resetting the client tears every subscription down.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonySubscribeTest : BaseTests() {
    private val mockCountryService by before { mock<CountryService>() }
    private val mockPhoneNumberSearchService by before { mock<PhoneNumberSearchService>() }
    private val mockPhoneNumberService by before { mock<PhoneNumberService>() }
    private val mockPhoneMessageService by before { mock<PhoneMessageService>() }
    private val mockPhoneMessageUnsealer by before { mock<PhoneMessageUnsealer>() }
    private val mockServiceKeyManager by before { mock<ServiceKeyManager>() }
    private val mockUseCaseFactory by before { mock<UseCaseFactory>() }
    private val mockSubscriptionService by before { mock<SubscriptionService>() }

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
            subscriptionService = mockSubscriptionService,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @Test
    fun `subscribeToPhoneNumbers() should delegate to the subscription service`() =
        runTest {
            val subscriber = mock<PhoneNumberSubscriber>()

            val deferred =
                async(StandardTestDispatcher(testScheduler)) {
                    client.subscribeToPhoneNumbers("sub-1", subscriber)
                }
            deferred.start()
            deferred.await()

            verify(mockSubscriptionService).subscribeToPhoneNumbers(eq("sub-1"), eq(subscriber))
        }

    @Test
    fun `subscribeToPhoneNumbers() should surface an authentication failure`() =
        runTest {
            val subscriber = mock<PhoneNumberSubscriber>()
            mockSubscriptionService.stub {
                onBlocking {
                    subscribeToPhoneNumbers(eq("sub-1"), eq(subscriber))
                } doThrow SudoTelephonyClient.PhoneNumberException.AuthenticationException()
            }

            val deferred =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoTelephonyClient.PhoneNumberException.AuthenticationException> {
                        client.subscribeToPhoneNumbers("sub-1", subscriber)
                    }
                }
            deferred.start()
            deferred.await()
        }

    @Test
    fun `unsubscribeFromPhoneNumbers() should delegate to the subscription service`() {
        client.unsubscribeFromPhoneNumbers("sub-1")

        verify(mockSubscriptionService).unsubscribeFromPhoneNumbers(eq("sub-1"))
    }

    @Test
    fun `unsubscribeAllFromPhoneNumbers() should delegate to the subscription service`() {
        client.unsubscribeAllFromPhoneNumbers()

        verify(mockSubscriptionService).unsubscribeAllFromPhoneNumbers()
    }

    @Test
    fun `subscribeToPhoneMessages() should delegate to the subscription service`() =
        runTest {
            val subscriber = mock<PhoneMessageSubscriber>()

            val deferred =
                async(StandardTestDispatcher(testScheduler)) {
                    client.subscribeToPhoneMessages("sub-1", subscriber)
                }
            deferred.start()
            deferred.await()

            verify(mockSubscriptionService).subscribeToPhoneMessages(eq("sub-1"), eq(subscriber))
        }

    @Test
    fun `unsubscribeFromPhoneMessages() should delegate to the subscription service`() {
        client.unsubscribeFromPhoneMessages("sub-1")

        verify(mockSubscriptionService).unsubscribeFromPhoneMessages(eq("sub-1"))
    }

    @Test
    fun `unsubscribeAllFromPhoneMessages() should delegate to the subscription service`() {
        client.unsubscribeAllFromPhoneMessages()

        verify(mockSubscriptionService).unsubscribeAllFromPhoneMessages()
    }

    @Test
    fun `close() should tear down every subscription`() {
        client.close()

        verify(mockSubscriptionService).close()
    }

    @Test
    fun `reset() should tear down every subscription`() {
        client.reset()

        verify(mockSubscriptionService).close()
    }
}
