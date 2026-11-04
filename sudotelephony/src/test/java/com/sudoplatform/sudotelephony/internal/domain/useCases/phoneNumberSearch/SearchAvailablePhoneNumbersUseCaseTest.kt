/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumberSearch

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberSearchException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
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
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SearchAvailablePhoneNumbersUseCase]: it maps its input onto
 * the [PhoneNumberSearchService] port, returns the service output, and propagates domain
 * exceptions unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class SearchAvailablePhoneNumbersUseCaseTest : BaseTests() {
    private val mockPhoneNumberSearchService by before {
        mock<PhoneNumberSearchService>()
    }

    private val instanceUnderTest by before {
        SearchAvailablePhoneNumbersUseCase(mockPhoneNumberSearchService, mockLogger)
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockPhoneNumberSearchService)
    }

    @Test
    fun `execute() should return the service output when successful`() =
        runTest {
            val entity = EntityDataFactory.phoneNumberSearchEntity()
            mockPhoneNumberSearchService.stub {
                onBlocking { searchAvailablePhoneNumbers(any()) } doReturn entity
            }

            val result =
                instanceUnderTest.execute(
                    SearchAvailablePhoneNumbersUseCaseInput(countryCode = "US", areaCode = "415"),
                )

            result shouldBe entity

            verify(mockPhoneNumberSearchService).searchAvailablePhoneNumbers(any())
        }

    @Test
    fun `execute() should map its input onto the service input`() =
        runTest {
            mockPhoneNumberSearchService.stub {
                onBlocking { searchAvailablePhoneNumbers(any()) } doReturn
                    EntityDataFactory.phoneNumberSearchEntity()
            }

            instanceUnderTest.execute(
                SearchAvailablePhoneNumbersUseCaseInput(
                    countryCode = "CA",
                    areaCode = "604",
                    limit = 7,
                ),
            )

            verify(mockPhoneNumberSearchService).searchAvailablePhoneNumbers(
                check {
                    it.countryCode shouldBe "CA"
                    it.areaCode shouldBe "604"
                    it.limit shouldBe 7
                },
            )
        }

    @Test
    fun `execute() should leave the optional input unset when the caller omits it`() =
        runTest {
            mockPhoneNumberSearchService.stub {
                onBlocking { searchAvailablePhoneNumbers(any()) } doReturn
                    EntityDataFactory.phoneNumberSearchEntity()
            }

            instanceUnderTest.execute(SearchAvailablePhoneNumbersUseCaseInput(countryCode = "US"))

            verify(mockPhoneNumberSearchService).searchAvailablePhoneNumbers(
                check {
                    it.countryCode shouldBe "US"
                    it.areaCode shouldBe null
                    it.limit shouldBe null
                },
            )
        }

    @Test
    fun `execute() should propagate a domain exception unchanged`() =
        runTest {
            mockPhoneNumberSearchService.stub {
                onBlocking { searchAvailablePhoneNumbers(any()) } doThrow
                    PhoneNumberSearchException.InvalidAreaCodeException("bad area code")
            }

            shouldThrow<PhoneNumberSearchException.InvalidAreaCodeException> {
                instanceUnderTest.execute(
                    SearchAvailablePhoneNumbersUseCaseInput(countryCode = "US", areaCode = "000"),
                )
            }

            verify(mockPhoneNumberSearchService).searchAvailablePhoneNumbers(any())
        }
}
