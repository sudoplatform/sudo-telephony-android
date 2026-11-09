/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberSearchException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumberSearch.SearchAvailablePhoneNumbersUseCase
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.types.PhoneNumberType
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
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
import java.util.Date

/**
 * Test the correct operation of [SudoTelephonyClient.searchAvailablePhoneNumbers] using mocks
 * and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonySearchAvailablePhoneNumbersTest : BaseTests() {
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
        mock<SearchAvailablePhoneNumbersUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn EntityDataFactory.phoneNumberSearchEntity()
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createSearchAvailablePhoneNumbersUseCase() } doReturn mockUseCase
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
    fun `searchAvailablePhoneNumbers() should return the search when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.searchAvailablePhoneNumbers(
                        SearchAvailablePhoneNumbersInput(countryCode = "US", areaCode = "415"),
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            with(result) {
                id shouldBe "mockSearchId"
                countryCode shouldBe "US"
                areaCode shouldBe "415"
                owner shouldBe "mockOwner"
                version shouldBe 1
                expiresAt shouldBe Date(EntityDataFactory.EXPIRES_AT_EPOCH_MS.toLong())
                results.size shouldBe 1
                results.first().phoneNumber shouldBe "+14155550123"
                results.first().numberType shouldBe PhoneNumberType.Local
            }

            verify(mockUseCaseFactory).createSearchAvailablePhoneNumbersUseCase()
            verify(mockUseCase).execute(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should pass the caller's input to the use case`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.searchAvailablePhoneNumbers(
                        SearchAvailablePhoneNumbersInput(
                            countryCode = "CA",
                            areaCode = "604",
                            limit = 3,
                        ),
                    )
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createSearchAvailablePhoneNumbersUseCase()
            verify(mockUseCase).execute(
                check {
                    it.countryCode shouldBe "CA"
                    it.areaCode shouldBe "604"
                    it.limit shouldBe 3
                },
            )
        }

    @Test
    fun `searchAvailablePhoneNumbers() should return an empty result list when nothing is available`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doReturn EntityDataFactory.phoneNumberSearchEntity(results = emptyList())
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.searchAvailablePhoneNumbers(
                        SearchAvailablePhoneNumbersInput(countryCode = "US", areaCode = "415"),
                    )
                }
            deferredResult.start()

            deferredResult.await().results shouldBe emptyList()

            verify(mockUseCaseFactory).createSearchAvailablePhoneNumbersUseCase()
            verify(mockUseCase).execute(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw when the use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doThrow PhoneNumberSearchException.UnsupportedCountryException()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<PhoneNumberSearchException.UnsupportedCountryException> {
                        client.searchAvailablePhoneNumbers(
                            SearchAvailablePhoneNumbersInput(countryCode = "ZZ", areaCode = "415"),
                        )
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createSearchAvailablePhoneNumbersUseCase()
            verify(mockUseCase).execute(any())
        }
}
