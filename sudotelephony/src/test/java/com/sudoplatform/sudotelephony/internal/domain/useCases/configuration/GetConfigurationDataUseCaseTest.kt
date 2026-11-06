/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.configuration

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.ConfigurationDataException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataService
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
 * Test the correct operation of [GetConfigurationDataUseCase]: it delegates to the
 * [ConfigurationDataService] port, returns the service output, and propagates domain
 * exceptions unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class GetConfigurationDataUseCaseTest : BaseTests() {
    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>()
    }

    private val instanceUnderTest by before {
        GetConfigurationDataUseCase(mockConfigurationDataService, mockLogger)
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockConfigurationDataService)
    }

    @Test
    fun `execute() should return the service output when successful`() =
        runTest {
            mockConfigurationDataService.stub {
                onBlocking { getConfigurationData() } doReturn EntityDataFactory.configurationDataEntity()
            }

            instanceUnderTest.execute() shouldBe EntityDataFactory.configurationDataEntity()

            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `execute() should propagate a domain exception unchanged`() =
        runTest {
            mockConfigurationDataService.stub {
                onBlocking { getConfigurationData() } doThrow ConfigurationDataException.ServiceException("down")
            }

            shouldThrow<ConfigurationDataException.ServiceException> {
                instanceUnderTest.execute()
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }
}
