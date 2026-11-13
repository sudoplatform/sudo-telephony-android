/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberStateEntity
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [DeprovisionPhoneNumberUseCase]: it delegates to the
 * [PhoneNumberService] port with the caller's id, removes the released number's key pairs
 * unless a singleton key pair is enforced, and propagates domain exceptions unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class DeprovisionPhoneNumberUseCaseTest : BaseTests() {
    private val mockPhoneNumberService by before {
        mock<PhoneNumberService>()
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>()
    }

    private val mockUseCaseLogger by before { mock<Logger>() }

    private val instanceUnderTest by before {
        DeprovisionPhoneNumberUseCase(mockPhoneNumberService, mockServiceKeyManager, mockLogger)
    }

    private val singletonInstanceUnderTest by before {
        DeprovisionPhoneNumberUseCase(
            mockPhoneNumberService,
            mockServiceKeyManager,
            mockLogger,
            enforceSingletonKeyPair = true,
        )
    }

    private val released by before {
        EntityDataFactory.phoneNumberEntity(
            state = PhoneNumberStateEntity.Released,
            keyIds = listOf("mockKeyId1", "mockKeyId2"),
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockPhoneNumberService, mockServiceKeyManager)
    }

    @Test
    fun `execute() should return the released number and remove its key pairs when successful`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { deprovision(any()) } doReturn released
            }

            instanceUnderTest.execute("mockPhoneNumberId") shouldBe released

            verify(mockPhoneNumberService).deprovision(eq("mockPhoneNumberId"))
            verify(mockServiceKeyManager).removeKeyPair(eq("mockKeyId1"))
            verify(mockServiceKeyManager).removeKeyPair(eq("mockKeyId2"))
        }

    @Test
    fun `execute() should remove no key pairs when a singleton key pair is enforced`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { deprovision(any()) } doReturn released
            }

            singletonInstanceUnderTest.execute("mockPhoneNumberId") shouldBe released

            verify(mockPhoneNumberService).deprovision(eq("mockPhoneNumberId"))
        }

    @Test
    fun `execute() should try every key pair then throw with the released number when removal fails`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { deprovision(any()) } doReturn released
            }
            val firstFailure = DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("locked")
            mockServiceKeyManager.stub {
                on { removeKeyPair(any()) } doThrow firstFailure doThrow
                    DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("still locked")
            }
            val useCase =
                DeprovisionPhoneNumberUseCase(mockPhoneNumberService, mockServiceKeyManager, mockUseCaseLogger)

            val thrown =
                shouldThrow<DeprovisionedKeyPairRemovalException> {
                    useCase.execute("mockPhoneNumberId")
                }

            thrown.deprovisioned shouldBe released
            thrown.cause shouldBe firstFailure
            verify(mockPhoneNumberService).deprovision(eq("mockPhoneNumberId"))
            verify(mockServiceKeyManager).removeKeyPair(eq("mockKeyId1"))
            verify(mockServiceKeyManager).removeKeyPair(eq("mockKeyId2"))
            verify(mockUseCaseLogger).error(argThat<String> { contains("mockKeyId1") })
            verify(mockUseCaseLogger).error(argThat<String> { contains("mockKeyId2") })
        }

    @Test
    fun `execute() should propagate a state exception with the reported state`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { deprovision(any()) } doThrow
                    PhoneNumberException.InvalidPhoneNumberStateException(
                        state = PhoneNumberState.Provisioning,
                    )
            }

            val exception =
                shouldThrow<PhoneNumberException.InvalidPhoneNumberStateException> {
                    instanceUnderTest.execute("mockPhoneNumberId")
                }
            exception.state shouldBe PhoneNumberState.Provisioning

            verify(mockPhoneNumberService).deprovision(eq("mockPhoneNumberId"))
        }

    @Test
    fun `execute() should propagate a domain exception unchanged and remove no key pairs`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { deprovision(any()) } doThrow
                    PhoneNumberException.PhoneNumberNotFoundException("gone")
            }

            shouldThrow<PhoneNumberException.PhoneNumberNotFoundException> {
                instanceUnderTest.execute("mockPhoneNumberId")
            }

            verify(mockPhoneNumberService).deprovision(eq("mockPhoneNumberId"))
        }
}
