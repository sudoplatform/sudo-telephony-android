/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.country.GetSupportedCountriesUseCase
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.types.inputs.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesForConversationIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesForPhoneNumberIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersForSudoIdInput
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.UpdatePhoneMessagesInput
import com.sudoplatform.sudouser.SudoPlatformSignInCallback
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoTelephonyClient.setSignInCallback]: the callback signs
 * the user in before an operation only when one is set and the user is signed out.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonySignInCallbackTest : BaseTests() {
    private class SignInFailed : RuntimeException()

    /** Counts its invocations, and throws when [failing] is set. */
    private class RecordingCallback(
        private val failing: Boolean = false,
    ) : SudoPlatformSignInCallback {
        var calls = 0

        override suspend fun signIn() {
            calls++
            if (failing) {
                throw SignInFailed()
            }
        }
    }

    private val mockUseCase by before {
        mock<GetSupportedCountriesUseCase>().stub {
            onBlocking { execute() } doReturn EntityDataFactory.countryCodes
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetSupportedCountriesUseCase() } doReturn mockUseCase
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>()
    }

    private val client by before {
        DefaultSudoTelephonyClient(
            context = mockContext,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            serviceKeyManager = mockServiceKeyManager,
            logger = mockLogger,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockUserClient, mockUseCaseFactory, mockUseCase)
    }

    private fun signedIn(value: Boolean) {
        mockUserClient.stub { on { isSignedIn() } doReturn value }
    }

    @Test
    fun `an operation should not check sign in when no callback is set`() =
        runTest {
            client.getSupportedCountries() shouldBe EntityDataFactory.countryCodes

            verify(mockUseCaseFactory).createGetSupportedCountriesUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `an operation should not invoke the callback when the user is signed in`() =
        runTest {
            val callback = RecordingCallback()
            signedIn(true)
            client.setSignInCallback(callback)

            client.getSupportedCountries() shouldBe EntityDataFactory.countryCodes

            callback.calls shouldBe 0
            verify(mockUserClient).isSignedIn()
            verify(mockUseCaseFactory).createGetSupportedCountriesUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `an operation should invoke the callback and then run when the user is signed out`() =
        runTest {
            val callback = RecordingCallback()
            signedIn(false)
            client.setSignInCallback(callback)

            client.getSupportedCountries() shouldBe EntityDataFactory.countryCodes

            callback.calls shouldBe 1
            verify(mockUserClient).isSignedIn()
            verify(mockUseCaseFactory).createGetSupportedCountriesUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `an operation should stop checking sign in once the callback is cleared`() =
        runTest {
            val callback = RecordingCallback()
            signedIn(false)
            client.setSignInCallback(callback)
            client.setSignInCallback(null)

            client.getSupportedCountries()

            callback.calls shouldBe 0
            verify(mockUseCaseFactory).createGetSupportedCountriesUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `every service operation should propagate a failed sign in without running`() =
        runTest {
            val operations: List<suspend () -> Unit> =
                listOf(
                    { client.getSupportedCountries() },
                    { client.getConfigurationData() },
                    { client.searchAvailablePhoneNumbers(SearchAvailablePhoneNumbersInput(countryCode = "US")) },
                    { client.provisionPhoneNumber(ProvisionPhoneNumberInput(searchId = "s", phoneNumber = "+14155550123")) },
                    { client.deprovisionPhoneNumber("id") },
                    { client.getPhoneNumber("id") },
                    { client.listPhoneNumbers() },
                    { client.listPhoneNumbersForSudoId(ListPhoneNumbersForSudoIdInput(sudoId = "sudo")) },
                    { client.getPhoneMessage("id") },
                    { client.listPhoneMessagesForPhoneNumberId(ListPhoneMessagesForPhoneNumberIdInput(phoneNumberId = "id")) },
                    { client.listPhoneMessagesForConversationId(ListPhoneMessagesForConversationIdInput(conversationId = "id")) },
                    { client.deletePhoneMessages(DeletePhoneMessagesInput(ids = listOf("id"))) },
                    { client.updatePhoneMessages(UpdatePhoneMessagesInput(ids = listOf("id"), seen = true)) },
                )
            val callback = RecordingCallback(failing = true)
            signedIn(false)
            client.setSignInCallback(callback)

            operations.forEach { operation ->
                shouldThrow<SignInFailed> { operation() }
            }

            callback.calls shouldBe operations.size
            verify(mockUserClient, times(operations.size)).isSignedIn()
        }

    @Test
    fun `key export should not check sign in`() =
        runTest {
            mockServiceKeyManager.stub { onBlocking { exportKeys() } doReturn byteArrayOf(1) }
            val callback = RecordingCallback(failing = true)
            client.setSignInCallback(callback)

            client.exportKeys() shouldBe byteArrayOf(1)

            callback.calls shouldBe 0
        }
}
