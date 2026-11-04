/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.country

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.SupportedCountriesException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
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
 * Test the correct operation of [GetSupportedCountriesUseCase]: it delegates to the
 * [CountryService] port, returns the service output, and propagates domain exceptions
 * unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class GetSupportedCountriesUseCaseTest : BaseTests() {
    private val mockCountryService by before {
        mock<CountryService>()
    }

    private val instanceUnderTest by before {
        GetSupportedCountriesUseCase(mockCountryService, mockLogger)
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockCountryService)
    }

    @Test
    fun `execute() should return the service output when successful`() =
        runTest {
            mockCountryService.stub {
                onBlocking { getSupportedCountries() } doReturn EntityDataFactory.countryCodes
            }

            instanceUnderTest.execute() shouldBe EntityDataFactory.countryCodes

            verify(mockCountryService).getSupportedCountries()
        }

    @Test
    fun `execute() should return an empty list when the service supports no countries`() =
        runTest {
            mockCountryService.stub {
                onBlocking { getSupportedCountries() } doReturn emptyList()
            }

            instanceUnderTest.execute() shouldBe emptyList()

            verify(mockCountryService).getSupportedCountries()
        }

    @Test
    fun `execute() should propagate a domain exception unchanged`() =
        runTest {
            mockCountryService.stub {
                onBlocking { getSupportedCountries() } doThrow
                    SupportedCountriesException.SupportedCountriesUnavailableException("unavailable")
            }

            shouldThrow<SupportedCountriesException.SupportedCountriesUnavailableException> {
                instanceUnderTest.execute()
            }

            verify(mockCountryService).getSupportedCountries()
        }
}
