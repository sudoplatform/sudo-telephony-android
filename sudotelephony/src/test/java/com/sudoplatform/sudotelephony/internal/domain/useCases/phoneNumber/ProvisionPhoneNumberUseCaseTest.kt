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
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [ProvisionPhoneNumberUseCase]: it gets the key pair that
 * seals the number's messages, passes it to the [PhoneNumberService] port with the caller's
 * input, removes a per-number key pair the service definitely rejected, and propagates domain
 * exceptions unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class ProvisionPhoneNumberUseCaseTest : BaseTests() {
    private val mockPhoneNumberService by before {
        mock<PhoneNumberService>()
    }

    private val keyPair by before { EntityDataFactory.keyPair() }

    private val singletonKeyPair by before { EntityDataFactory.keyPair(keyId = "singletonKeyId") }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { generateKeyPair() } doReturn keyPair
            on { getSingletonKeyPair() } doReturn singletonKeyPair
        }
    }

    private val mockUseCaseLogger by before { mock<Logger>() }

    private val instanceUnderTest by before {
        ProvisionPhoneNumberUseCase(mockPhoneNumberService, mockServiceKeyManager, mockLogger)
    }

    private val singletonInstanceUnderTest by before {
        ProvisionPhoneNumberUseCase(
            mockPhoneNumberService,
            mockServiceKeyManager,
            mockLogger,
            enforceSingletonKeyPair = true,
        )
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

    private fun givenProvisionFails(e: Throwable) {
        mockPhoneNumberService.stub {
            onBlocking { provision(any()) } doThrow e
        }
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
    fun `execute() should pass the singleton key pair to the service when it is enforced`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { provision(any()) } doReturn EntityDataFactory.phoneNumberEntity()
            }

            singletonInstanceUnderTest.execute(input)

            verify(mockServiceKeyManager).getSingletonKeyPair()
            verify(mockPhoneNumberService).provision(
                check {
                    it.keyPair shouldBe singletonKeyPair
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

            val thrown =
                shouldThrow<PhoneNumberException.UnknownException> {
                    instanceUnderTest.execute(input)
                }

            (thrown.cause is DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException) shouldBe true
            verify(mockServiceKeyManager).generateKeyPair()
            verifyNoInteractions(mockPhoneNumberService)
        }

    @Test
    fun `execute() should fail without calling the service when the singleton key pair is absent`() =
        runTest {
            mockServiceKeyManager.stub { on { getSingletonKeyPair() } doReturn null }

            shouldThrow<PhoneNumberException.SingletonKeyPairNotFoundException> {
                singletonInstanceUnderTest.execute(input)
            }

            verify(mockServiceKeyManager).getSingletonKeyPair()
            verifyNoInteractions(mockPhoneNumberService)
        }

    @Test
    fun `execute() should fail without calling the service when the singleton key pair cannot be read`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getSingletonKeyPair() } doThrow
                    DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("no public key")
            }

            val thrown =
                shouldThrow<PhoneNumberException.UnknownException> {
                    singletonInstanceUnderTest.execute(input)
                }

            (thrown.cause is DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException) shouldBe true
            verify(mockServiceKeyManager).getSingletonKeyPair()
            verifyNoInteractions(mockPhoneNumberService)
        }

    @Test
    fun `execute() should remove the key pair when the number is unavailable`() =
        runTest {
            givenProvisionFails(PhoneNumberException.PhoneNumberUnavailableException("taken"))

            shouldThrow<PhoneNumberException.PhoneNumberUnavailableException> {
                instanceUnderTest.execute(input)
            }

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockPhoneNumberService).provision(any())
            verify(mockServiceKeyManager).removeKeyPair(eq(keyPair.keyId))
        }

    @Test
    fun `execute() should remove the key pair when the caller is not authenticated`() =
        runTest {
            givenProvisionFails(PhoneNumberException.AuthenticationException("signed out"))

            shouldThrow<PhoneNumberException.AuthenticationException> {
                instanceUnderTest.execute(input)
            }

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockPhoneNumberService).provision(any())
            verify(mockServiceKeyManager).removeKeyPair(eq(keyPair.keyId))
        }

    @Test
    fun `execute() should keep the key pair after a failure that may follow a provisioned number`() =
        runTest {
            val failures =
                listOf(
                    PhoneNumberException.FailedException("5xx"),
                    PhoneNumberException.ServiceException("internal"),
                    PhoneNumberException.UnknownException(RuntimeException("transport")),
                    PhoneNumberException.ProvisionFailedException("no data"),
                )
            for (failure in failures) {
                givenProvisionFails(failure)

                val thrown =
                    shouldThrow<PhoneNumberException> {
                        instanceUnderTest.execute(input)
                    }
                thrown shouldBe failure
            }

            verify(mockServiceKeyManager, times(failures.size)).generateKeyPair()
            verify(mockPhoneNumberService, times(failures.size)).provision(any())
        }

    @Test
    fun `execute() should never remove the singleton key pair`() =
        runTest {
            givenProvisionFails(PhoneNumberException.PhoneNumberUnavailableException("taken"))

            shouldThrow<PhoneNumberException.PhoneNumberUnavailableException> {
                singletonInstanceUnderTest.execute(input)
            }

            verify(mockServiceKeyManager).getSingletonKeyPair()
            verify(mockPhoneNumberService).provision(any())
        }

    @Test
    fun `execute() should report the provisioning exception and log a warning when removal fails`() =
        runTest {
            val rejection = PhoneNumberException.PhoneNumberUnavailableException("taken")
            givenProvisionFails(rejection)
            mockServiceKeyManager.stub {
                on { removeKeyPair(any()) } doThrow
                    DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("locked")
            }
            val useCase =
                ProvisionPhoneNumberUseCase(mockPhoneNumberService, mockServiceKeyManager, mockUseCaseLogger)

            val thrown =
                shouldThrow<PhoneNumberException.PhoneNumberUnavailableException> {
                    useCase.execute(input)
                }
            thrown shouldBe rejection

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockPhoneNumberService).provision(any())
            verify(mockServiceKeyManager).removeKeyPair(eq(keyPair.keyId))
            verify(mockUseCaseLogger).warning(argThat<String> { contains(keyPair.keyId) })
        }
}
