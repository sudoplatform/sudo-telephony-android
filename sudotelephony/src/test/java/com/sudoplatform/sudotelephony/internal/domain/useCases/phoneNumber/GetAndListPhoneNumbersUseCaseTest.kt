/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ListPhoneNumbersOutput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [GetPhoneNumberUseCase] and [ListPhoneNumbersUseCase]: each
 * delegates to the [PhoneNumberService] port and propagates domain exceptions unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class GetAndListPhoneNumbersUseCaseTest : BaseTests() {
    private val mockPhoneNumberService by before {
        mock<PhoneNumberService>()
    }

    private val getUseCase by before {
        GetPhoneNumberUseCase(mockPhoneNumberService, mockLogger)
    }

    private val listUseCase by before {
        ListPhoneNumbersUseCase(mockPhoneNumberService, mockLogger)
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockPhoneNumberService)
    }

    @Test
    fun `GetPhoneNumberUseCase execute() should return the number when it exists`() =
        runTest {
            val entity = EntityDataFactory.phoneNumberEntity()
            mockPhoneNumberService.stub {
                onBlocking { get(any()) } doReturn entity
            }

            getUseCase.execute("mockPhoneNumberId") shouldBe entity

            verify(mockPhoneNumberService).get(eq("mockPhoneNumberId"))
        }

    @Test
    fun `GetPhoneNumberUseCase execute() should return null when the number is absent`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { get(any()) } doReturn null
            }

            getUseCase.execute("mockPhoneNumberId") shouldBe null

            verify(mockPhoneNumberService).get(any())
        }

    @Test
    fun `GetPhoneNumberUseCase execute() should propagate a domain exception unchanged`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { get(any()) } doThrow PhoneNumberException.FailedException("boom")
            }

            shouldThrow<PhoneNumberException.FailedException> {
                getUseCase.execute("mockPhoneNumberId")
            }

            verify(mockPhoneNumberService).get(any())
        }

    @Test
    fun `ListPhoneNumbersUseCase execute() should return the service page`() =
        runTest {
            val output =
                ListPhoneNumbersOutput(
                    items = listOf(EntityDataFactory.phoneNumberEntity()),
                    nextToken = "mockNextToken",
                )
            mockPhoneNumberService.stub {
                onBlocking { list(any()) } doReturn output
            }

            val result = listUseCase.execute(ListPhoneNumbersUseCaseInput())

            result shouldNotBe null
            result shouldBe output

            verify(mockPhoneNumberService).list(any())
        }

    @Test
    fun `ListPhoneNumbersUseCase execute() should map its paging input onto the service input`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { list(any()) } doReturn ListPhoneNumbersOutput(items = emptyList())
            }

            listUseCase.execute(ListPhoneNumbersUseCaseInput(limit = 7, nextToken = "token"))

            verify(mockPhoneNumberService).list(
                check {
                    it.limit shouldBe 7
                    it.nextToken shouldBe "token"
                },
            )
        }

    @Test
    fun `ListPhoneNumbersUseCase execute() should leave the paging input unset when omitted`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { list(any()) } doReturn ListPhoneNumbersOutput(items = emptyList())
            }

            listUseCase.execute(ListPhoneNumbersUseCaseInput())

            verify(mockPhoneNumberService).list(
                check {
                    it.limit shouldBe null
                    it.nextToken shouldBe null
                },
            )
        }

    @Test
    fun `ListPhoneNumbersUseCase execute() should propagate a domain exception unchanged`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { list(any()) } doThrow
                    PhoneNumberException.InvalidArgumentException("bad token")
            }

            shouldThrow<PhoneNumberException.InvalidArgumentException> {
                listUseCase.execute(ListPhoneNumbersUseCaseInput(nextToken = "bogus"))
            }

            verify(mockPhoneNumberService).list(any())
        }

    @Test
    fun `ListPhoneNumbersUseCase execute(sudoId) should map its input onto the service input and return the page`() =
        runTest {
            val output =
                ListPhoneNumbersOutput(
                    items = listOf(EntityDataFactory.phoneNumberEntity()),
                    nextToken = "mockNextToken",
                )
            mockPhoneNumberService.stub {
                onBlocking { listForSudoId(any()) } doReturn output
            }

            listUseCase.execute(
                ListPhoneNumbersForSudoIdUseCaseInput(sudoId = "mockSudoId", limit = 7, nextToken = "token"),
            ) shouldBe output

            verify(mockPhoneNumberService).listForSudoId(
                check {
                    it.sudoId shouldBe "mockSudoId"
                    it.limit shouldBe 7
                    it.nextToken shouldBe "token"
                },
            )
        }

    @Test
    fun `ListPhoneNumbersUseCase execute(sudoId) should propagate a domain exception unchanged`() =
        runTest {
            mockPhoneNumberService.stub {
                onBlocking { listForSudoId(any()) } doThrow
                    PhoneNumberException.InvalidArgumentException("empty sudoId")
            }

            shouldThrow<PhoneNumberException.InvalidArgumentException> {
                listUseCase.execute(ListPhoneNumbersForSudoIdUseCaseInput(sudoId = ""))
            }

            verify(mockPhoneNumberService).listForSudoId(any())
        }
}
