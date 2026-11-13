/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.SudoTelephonyClient.CryptographicKeysException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.keys.CreateSingletonKeysIfAbsentUseCase
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.keys.SingletonKeyPairResult
import com.sudoplatform.sudotelephony.types.CreateSingletonKeysIfAbsentResult
import com.sudoplatform.sudotelephony.types.KeyResult
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
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
 * Test the correct operation of [SudoTelephonyClient.createSingletonKeysIfAbsent] using mocks.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonyCreateSingletonKeysIfAbsentTest : BaseTests() {
    private val mockServiceKeyManager by before { mock<ServiceKeyManager>() }

    private val mockUseCase by before { mock<CreateSingletonKeysIfAbsentUseCase>() }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createCreateSingletonKeysIfAbsentUseCase() } doReturn mockUseCase
        }
    }

    private val client by before {
        DefaultSudoTelephonyClient(
            context = mockContext,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            serviceKeyManager = mockServiceKeyManager,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockServiceKeyManager, mockUseCaseFactory, mockUseCase)
    }

    @Test
    fun `createSingletonKeysIfAbsent() should report a key pair it created`() =
        runTest {
            mockUseCase.stub {
                on { execute() } doReturn
                    SingletonKeyPairResult(EntityDataFactory.keyPair(keyId = "singletonKeyId"), created = true)
            }

            client.createSingletonKeysIfAbsent() shouldBe
                CreateSingletonKeysIfAbsentResult(KeyResult(created = true, keyId = "singletonKeyId"))

            verify(mockUseCaseFactory).createCreateSingletonKeysIfAbsentUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `createSingletonKeysIfAbsent() should report a key pair that already existed`() =
        runTest {
            mockUseCase.stub {
                on { execute() } doReturn
                    SingletonKeyPairResult(EntityDataFactory.keyPair(keyId = "singletonKeyId"), created = false)
            }

            client.createSingletonKeysIfAbsent() shouldBe
                CreateSingletonKeysIfAbsentResult(KeyResult(created = false, keyId = "singletonKeyId"))

            verify(mockUseCaseFactory).createCreateSingletonKeysIfAbsentUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `createSingletonKeysIfAbsent() should surface a configuration failure unchanged`() =
        runTest {
            val failure = CryptographicKeysException.InvalidConfigurationException("not enforced")
            mockUseCase.stub { on { execute() } doThrow failure }

            shouldThrow<CryptographicKeysException.InvalidConfigurationException> {
                client.createSingletonKeysIfAbsent()
            } shouldBe failure

            verify(mockUseCaseFactory).createCreateSingletonKeysIfAbsentUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `createSingletonKeysIfAbsent() should wrap a key store failure`() =
        runTest {
            val failure = DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException("no keys")
            mockUseCase.stub { on { execute() } doThrow failure }

            val thrown =
                shouldThrow<CryptographicKeysException.UnknownException> {
                    client.createSingletonKeysIfAbsent()
                }

            thrown.cause shouldBe failure
            verify(mockUseCaseFactory).createCreateSingletonKeysIfAbsentUseCase()
            verify(mockUseCase).execute()
        }
}
