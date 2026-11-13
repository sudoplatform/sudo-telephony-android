/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.keys

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.CryptographicKeysException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.keys.SingletonKeyPairResult
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
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
 * Test the correct operation of [CreateSingletonKeysIfAbsentUseCase]: it creates the singleton
 * key pair only for a client that enforces one.
 */
@RunWith(RobolectricTestRunner::class)
class CreateSingletonKeysIfAbsentUseCaseTest : BaseTests() {
    private val mockServiceKeyManager by before { mock<ServiceKeyManager>() }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockServiceKeyManager)
    }

    @Test
    fun `execute() should create the singleton key pair when it is enforced`() {
        val result = SingletonKeyPairResult(EntityDataFactory.keyPair(keyId = "singletonKeyId"), created = true)
        mockServiceKeyManager.stub { on { createSingletonKeyPairIfAbsent() } doReturn result }

        CreateSingletonKeysIfAbsentUseCase(mockServiceKeyManager, enforceSingletonKeyPair = true, mockLogger)
            .execute() shouldBe result

        verify(mockServiceKeyManager).createSingletonKeyPairIfAbsent()
    }

    @Test
    fun `execute() should throw InvalidConfigurationException without touching keys when it is not enforced`() {
        shouldThrow<CryptographicKeysException.InvalidConfigurationException> {
            CreateSingletonKeysIfAbsentUseCase(mockServiceKeyManager, enforceSingletonKeyPair = false, mockLogger)
                .execute()
        }
    }

    @Test
    fun `execute() should propagate a key store failure unchanged`() {
        val failure = DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException("no keys")
        mockServiceKeyManager.stub { on { createSingletonKeyPairIfAbsent() } doThrow failure }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException> {
            CreateSingletonKeysIfAbsentUseCase(mockServiceKeyManager, enforceSingletonKeyPair = true, mockLogger)
                .execute()
        } shouldBe failure

        verify(mockServiceKeyManager).createSingletonKeyPairIfAbsent()
    }
}
