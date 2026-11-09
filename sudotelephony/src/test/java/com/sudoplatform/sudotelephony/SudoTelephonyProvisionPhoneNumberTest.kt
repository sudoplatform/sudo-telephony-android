/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.ProvisionPhoneNumberUseCase
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.PhoneNumberType
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
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
 * Test the correct operation of [SudoTelephonyClient.provisionPhoneNumber] using mocks and
 * spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonyProvisionPhoneNumberTest : BaseTests() {
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
        mock<ProvisionPhoneNumberUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn EntityDataFactory.phoneNumberEntity()
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createProvisionPhoneNumberUseCase() } doReturn mockUseCase
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

    private val input =
        ProvisionPhoneNumberInput(
            searchId = "mockSearchId",
            phoneNumber = "+14155550123",
        )

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockApiClient,
            mockServiceKeyManager,
            mockUseCaseFactory,
            mockUseCase,
        )
    }

    @Test
    fun `provisionPhoneNumber() should return the number when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.provisionPhoneNumber(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            with(result) {
                id shouldBe "mockPhoneNumberId"
                phoneNumber shouldBe "+14155550123"
                countryCode shouldBe "US"
                areaCode shouldBe "415"
                numberType shouldBe PhoneNumberType.Local
                state shouldBe PhoneNumberState.Provisioning
                keyRingId shouldBe "sudo-telephony.mockOwner"
                keyIds shouldBe listOf("mockKeyId")
                owners.first().issuer shouldBe "sudoplatform.sudoservice"
            }

            verify(mockUseCaseFactory).createProvisionPhoneNumberUseCase()
            verify(mockUseCase).execute(any())
        }

    @Test
    fun `provisionPhoneNumber() should pass the caller's input to the use case`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.provisionPhoneNumber(
                        input.copy(ownershipProofToken = "mockProof", clientRefId = "mockRef"),
                    )
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createProvisionPhoneNumberUseCase()
            verify(mockUseCase).execute(
                check {
                    it.searchId shouldBe "mockSearchId"
                    it.phoneNumber shouldBe "+14155550123"
                    it.ownershipProofToken shouldBe "mockProof"
                    it.clientRefId shouldBe "mockRef"
                },
            )
        }

    @Test
    fun `provisionPhoneNumber() should throw when the use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doThrow PhoneNumberException.PhoneNumberSearchExpiredException()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<PhoneNumberException.PhoneNumberSearchExpiredException> {
                        client.provisionPhoneNumber(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createProvisionPhoneNumberUseCase()
            verify(mockUseCase).execute(any())
        }
}
