/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ListPhoneNumbersOutput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.GetPhoneNumberUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.ListPhoneNumbersUseCase
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersInput
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
 * Test the correct operation of [SudoTelephonyClient.getPhoneNumber] and
 * [SudoTelephonyClient.listPhoneNumbers] using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonyGetAndListPhoneNumbersTest : BaseTests() {
    private val mockCountryService by before { mock<CountryService>() }
    private val mockPhoneNumberSearchService by before { mock<PhoneNumberSearchService>() }
    private val mockPhoneNumberService by before { mock<PhoneNumberService>() }
    private val mockServiceKeyManager by before { mock<ServiceKeyManager>() }

    private val mockGetUseCase by before {
        mock<GetPhoneNumberUseCase>().stub {
            onBlocking { execute(any()) } doReturn EntityDataFactory.phoneNumberEntity()
        }
    }

    private val mockListUseCase by before {
        mock<ListPhoneNumbersUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn
                ListPhoneNumbersOutput(
                    items = listOf(EntityDataFactory.phoneNumberEntity()),
                    nextToken = "mockNextToken",
                )
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetPhoneNumberUseCase() } doReturn mockGetUseCase
            on { createListPhoneNumbersUseCase() } doReturn mockListUseCase
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
            mockServiceKeyManager,
            mockUseCaseFactory,
            mockGetUseCase,
            mockListUseCase,
        )
    }

    @Test
    fun `getPhoneNumber() should return the number when it exists`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getPhoneNumber("mockPhoneNumberId")
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result!!.id shouldBe "mockPhoneNumberId"
            result.state shouldBe PhoneNumberState.PROVISIONING

            verify(mockUseCaseFactory).createGetPhoneNumberUseCase()
            verify(mockGetUseCase).execute(check { it shouldBe "mockPhoneNumberId" })
        }

    @Test
    fun `getPhoneNumber() should return null when the number is absent`() =
        runTest {
            mockGetUseCase.stub {
                onBlocking { execute(any()) } doReturn null
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getPhoneNumber("mockPhoneNumberId")
                }
            deferredResult.start()

            deferredResult.await() shouldBe null

            verify(mockUseCaseFactory).createGetPhoneNumberUseCase()
            verify(mockGetUseCase).execute(any())
        }

    @Test
    fun `getPhoneNumber() should throw when the use case throws`() =
        runTest {
            mockGetUseCase.stub {
                onBlocking { execute(any()) } doThrow PhoneNumberException.FailedException()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<PhoneNumberException.FailedException> {
                        client.getPhoneNumber("mockPhoneNumberId")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createGetPhoneNumberUseCase()
            verify(mockGetUseCase).execute(any())
        }

    @Test
    fun `listPhoneNumbers() should return the page and its next token`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listPhoneNumbers()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result.items.size shouldBe 1
            result.items.first().id shouldBe "mockPhoneNumberId"
            result.nextToken shouldBe "mockNextToken"

            verify(mockUseCaseFactory).createListPhoneNumbersUseCase()
            verify(mockListUseCase).execute(any())
        }

    @Test
    fun `listPhoneNumbers() should pass the caller's paging parameters to the use case`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listPhoneNumbers(ListPhoneNumbersInput(limit = 3, nextToken = "token"))
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListPhoneNumbersUseCase()
            verify(mockListUseCase).execute(
                check {
                    it.limit shouldBe 3
                    it.nextToken shouldBe "token"
                },
            )
        }

    @Test
    fun `listPhoneNumbers() should return an empty page`() =
        runTest {
            mockListUseCase.stub {
                onBlocking { execute(any()) } doReturn ListPhoneNumbersOutput(items = emptyList())
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listPhoneNumbers()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result.items shouldBe emptyList()
            result.nextToken shouldBe null

            verify(mockUseCaseFactory).createListPhoneNumbersUseCase()
            verify(mockListUseCase).execute(any())
        }

    @Test
    fun `listPhoneNumbers() should throw when the use case throws`() =
        runTest {
            mockListUseCase.stub {
                onBlocking { execute(any()) } doThrow PhoneNumberException.InvalidArgumentException()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<PhoneNumberException.InvalidArgumentException> {
                        client.listPhoneNumbers(ListPhoneNumbersInput(nextToken = "bogus"))
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListPhoneNumbersUseCase()
            verify(mockListUseCase).execute(any())
        }
}
