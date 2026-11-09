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
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberStateEntity
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
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
 * [PhoneNumberService] port with the caller's id and propagates domain exceptions unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class DeprovisionPhoneNumberUseCaseTest : BaseTests() {
    private val mockPhoneNumberService by before {
        mock<PhoneNumberService>()
    }

    private val instanceUnderTest by before {
        DeprovisionPhoneNumberUseCase(mockPhoneNumberService, mockLogger)
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockPhoneNumberService)
    }

    @Test
    fun `execute() should return the released number when successful`() =
        runTest {
            val entity =
                EntityDataFactory.phoneNumberEntity(state = PhoneNumberStateEntity.Released)
            mockPhoneNumberService.stub {
                onBlocking { deprovision(any()) } doReturn entity
            }

            instanceUnderTest.execute("mockPhoneNumberId") shouldBe entity

            verify(mockPhoneNumberService).deprovision(eq("mockPhoneNumberId"))
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
    fun `execute() should propagate a domain exception unchanged`() =
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
