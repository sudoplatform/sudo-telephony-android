/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.SudoTelephonyClient.ConfigurationDataException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.configuration.GetConfigurationDataUseCase
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.types.ConfigurationData
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoTelephonyClient.getConfigurationData] using mocks and
 * spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonyGetConfigurationDataTest : BaseTests() {
    private val mockUseCase by before {
        mock<GetConfigurationDataUseCase>().stub {
            onBlocking {
                execute()
            } doReturn EntityDataFactory.configurationDataEntity()
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetConfigurationDataUseCase() } doReturn mockUseCase
        }
    }

    private val client by before {
        DefaultSudoTelephonyClient(
            context = mockContext,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            countryService = mock<CountryService>(),
            configurationDataService = mock<ConfigurationDataService>(),
            phoneNumberSearchService = mock<PhoneNumberSearchService>(),
            phoneNumberService = mock<PhoneNumberService>(),
            serviceKeyManager = mock<ServiceKeyManager>(),
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockApiClient,
            mockUseCaseFactory,
            mockUseCase,
        )
    }

    @Test
    fun `getConfigurationData() should return the configuration when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }
            deferredResult.start()

            deferredResult.await() shouldBe
                ConfigurationData(
                    sudoOwnershipProofRequired = true,
                    phoneNumberSearchDefaultLimit = 10,
                    phoneNumberSearchMaxLimit = 50,
                    phoneNumberSearchLifetimeMs = 900_000,
                    listDefaultLimit = 20,
                    listMaxLimit = 100,
                    deletePhoneMessagesLimit = 100,
                    updatePhoneMessagesLimit = 100,
                )

            verify(mockUseCaseFactory).createGetConfigurationDataUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getConfigurationData() should throw when the use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute()
                } doThrow ConfigurationDataException.ServiceException()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<ConfigurationDataException.ServiceException> {
                        client.getConfigurationData()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createGetConfigurationDataUseCase()
            verify(mockUseCase).execute()
        }
}
