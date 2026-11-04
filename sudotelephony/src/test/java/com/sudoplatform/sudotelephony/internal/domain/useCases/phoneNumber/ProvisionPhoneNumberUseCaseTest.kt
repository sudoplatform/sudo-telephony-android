/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [ProvisionPhoneNumberUseCase]: it generates the key pair that
 * seals the number's messages, passes it to the [PhoneNumberService] port with the caller's
 * input, and propagates domain exceptions unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class ProvisionPhoneNumberUseCaseTest : BaseTests() {
    private val mockPhoneNumberService by before {
        mock<PhoneNumberService>()
    }

    private val keyPair by before { EntityDataFactory.keyPair() }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { generateKeyPair() } doReturn keyPair
        }
    }

    private val instanceUnderTest by before {
        ProvisionPhoneNumberUseCase(mockPhoneNumberService, mockServiceKeyManager, mockLogger)
    }

    private val input =
        ProvisionPhoneNumberUseCaseInput(
            searchId = "mockSearchId",
            phoneNumber = "+14155550123",
        )

    @After
    fun fini() {
        verifyNoMoreInteractions(mockPhoneNumberService, mockServiceKeyManager)
    }

    @Test
    fun `execute() should return the service output when successful`() =
        runTest {
            val entity = EntityDataFactory.phoneNumberEntity()
            mockPhoneNumberService.stub {
                onBlocking { provision(any()) } doReturn entity
            }

            instanceUnderTest.execute(input) shouldBe entity

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockPhoneNumberService).provision(any())
        }

    @Test
    fun `execute() should generate a key pair and pass it to the service`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { provision(any()) } doReturn EntityDataFactory.phoneNumberEntity()
            }

            instanceUnderTest.execute(input)

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockPhoneNumberService).provision(
                check {
                    it.keyPair shouldBe keyPair
                },
            )
        }

    @Test
    fun `execute() should map its input onto the service request`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { provision(any()) } doReturn EntityDataFactory.phoneNumberEntity()
            }

            instanceUnderTest.execute(
                input.copy(ownershipProofToken = "mockProof", clientRefId = "mockRef"),
            )

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockPhoneNumberService).provision(
                check {
                    it.searchId shouldBe "mockSearchId"
                    it.phoneNumber shouldBe "+14155550123"
                    it.ownershipProofToken shouldBe "mockProof"
                    it.clientRefId shouldBe "mockRef"
                },
            )
        }

    @Test
    fun `execute() should leave the optional input unset when the caller omits it`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { provision(any()) } doReturn EntityDataFactory.phoneNumberEntity()
            }

            instanceUnderTest.execute(input)

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockPhoneNumberService).provision(
                check {
                    it.ownershipProofToken shouldBe null
                    it.clientRefId shouldBe null
                },
            )
        }

    @Test
    fun `execute() should not call the service when the key pair cannot be generated`() =
        runTest {
            mockServiceKeyManager.stub {
                on { generateKeyPair() } doThrow
                    DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException("no keys")
            }

            shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException> {
                instanceUnderTest.execute(input)
            }

            verify(mockServiceKeyManager).generateKeyPair()
            verifyNoInteractions(mockPhoneNumberService)
        }

    @Test
    fun `execute() should propagate a domain exception unchanged`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { provision(any()) } doThrow
                    PhoneNumberException.PhoneNumberUnavailableException("taken")
            }

            shouldThrow<PhoneNumberException.PhoneNumberUnavailableException> {
                instanceUnderTest.execute(input)
            }

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockPhoneNumberService).provision(any())
        }
}
