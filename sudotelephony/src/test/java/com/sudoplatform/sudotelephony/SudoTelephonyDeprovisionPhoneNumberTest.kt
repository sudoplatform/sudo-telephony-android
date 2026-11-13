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
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberStateEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.DeprovisionPhoneNumberUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.DeprovisionedKeyPairRemovalException
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.PhoneNumberType
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
 * Test the correct operation of [SudoTelephonyClient.deprovisionPhoneNumber] using mocks and
 * spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonyDeprovisionPhoneNumberTest : BaseTests() {
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
        mock<DeprovisionPhoneNumberUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn EntityDataFactory.phoneNumberEntity(state = PhoneNumberStateEntity.Released)
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createDeprovisionPhoneNumberUseCase() } doReturn mockUseCase
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
            mockUseCase,
        )
    }

    @Test
    fun `deprovisionPhoneNumber() should return the released number when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deprovisionPhoneNumber("mockPhoneNumberId")
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id shouldBe "mockPhoneNumberId"
            result.state shouldBe PhoneNumberState.Released

            verify(mockUseCaseFactory).createDeprovisionPhoneNumberUseCase()
            verify(mockUseCase).execute(any())
        }

    @Test
    fun `deprovisionPhoneNumber() should pass the caller's id to the use case`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deprovisionPhoneNumber("anotherPhoneNumberId")
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createDeprovisionPhoneNumberUseCase()
            verify(mockUseCase).execute(
                check {
                    it shouldBe "anotherPhoneNumberId"
                },
            )
        }

    @Test
    fun `deprovisionPhoneNumber() should report a key pair removal failure with the released number`() =
        runTest {
            val failure = DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("locked")
            mockUseCase.stub {
                onBlocking { execute(any()) } doThrow
                    DeprovisionedKeyPairRemovalException(
                        EntityDataFactory.phoneNumberEntity(state = PhoneNumberStateEntity.Released),
                        failure,
                    )
            }

            val thrown =
                shouldThrow<PhoneNumberException.KeyPairRemovalFailedException> {
                    client.deprovisionPhoneNumber("mockPhoneNumberId")
                }

            thrown.deprovisioned.id shouldBe "mockPhoneNumberId"
            thrown.deprovisioned.state shouldBe PhoneNumberState.Released
            thrown.cause shouldBe failure
            verify(mockUseCaseFactory).createDeprovisionPhoneNumberUseCase()
            verify(mockUseCase).execute(any())
        }

    @Test
    fun `deprovisionPhoneNumber() should throw when the use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doThrow
                    PhoneNumberException.InvalidPhoneNumberStateException(
                        state = PhoneNumberState.Provisioning,
                    )
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<PhoneNumberException.InvalidPhoneNumberStateException> {
                        client.deprovisionPhoneNumber("mockPhoneNumberId")
                    }
                }
            deferredResult.start()
            deferredResult.await().state shouldBe PhoneNumberState.Provisioning

            verify(mockUseCaseFactory).createDeprovisionPhoneNumberUseCase()
            verify(mockUseCase).execute(any())
        }
}
