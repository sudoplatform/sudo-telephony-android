/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.SudoTelephonyClient.SupportedCountriesException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.country.GetSupportedCountriesUseCase
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
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
 * Test the correct operation of [SudoTelephonyClient.getSupportedCountries]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonyGetSupportedCountriesTest : BaseTests() {
    private val mockCountryService by before {
        mock<CountryService>()
    }

    private val mockPhoneNumberSearchService by before {
        mock<PhoneNumberSearchService>()
    }

    private val mockPhoneNumberService by before {
        mock<PhoneNumberService>()
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>()
    }

    private val mockUseCase by before {
        mock<GetSupportedCountriesUseCase>().stub {
            onBlocking {
                execute()
            } doReturn EntityDataFactory.countryCodes
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetSupportedCountriesUseCase() } doReturn mockUseCase
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
            mockUseCaseFactory,
            mockUseCase,
        )
    }

    @Test
    fun `getSupportedCountries() should return the country codes when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getSupportedCountries()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result shouldBe EntityDataFactory.countryCodes

            verify(mockUseCaseFactory).createGetSupportedCountriesUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getSupportedCountries() should return an empty list when the service supports no countries`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute()
                } doReturn emptyList()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getSupportedCountries()
                }
            deferredResult.start()

            deferredResult.await() shouldBe emptyList()

            verify(mockUseCaseFactory).createGetSupportedCountriesUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getSupportedCountries() should throw when the use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute()
                } doThrow SupportedCountriesException.SupportedCountriesUnavailableException()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SupportedCountriesException.SupportedCountriesUnavailableException> {
                        client.getSupportedCountries()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createGetSupportedCountriesUseCase()
            verify(mockUseCase).execute()
        }
}
