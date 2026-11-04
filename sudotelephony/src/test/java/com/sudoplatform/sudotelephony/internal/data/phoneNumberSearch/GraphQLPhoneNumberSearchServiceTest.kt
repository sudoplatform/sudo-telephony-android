/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberSearchException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.data.DataFactory
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.SearchAvailablePhoneNumbersInput
import com.sudoplatform.sudouser.exceptions.HTTP_STATUS_CODE_KEY
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [GraphQLPhoneNumberSearchService]: the right query is
 * executed with the caller's criteria, the response fragment is transformed to an entity, and
 * GraphQL and throwable errors are interpreted into the expected
 * [PhoneNumberSearchException] subclass.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLPhoneNumberSearchServiceTest : BaseTests() {
    override val mockApiClient by before {
        mock<ApiClient>()
    }

    private val instanceUnderTest by before {
        GraphQLPhoneNumberSearchService(
            mockApiClient,
            mockLogger,
        )
    }

    private val input = SearchAvailablePhoneNumbersInput(countryCode = "US", areaCode = "415", limit = 5)

    @After
    fun fini() {
        verifyNoMoreInteractions(mockApiClient)
    }

    @Test
    fun `searchAvailablePhoneNumbers() should return the mapped entity when successful`() =
        runTest {
            mockApiClient.stub {
                onBlocking { searchAvailablePhoneNumbersQuery(any()) } doReturn
                    DataFactory.searchAvailablePhoneNumbersQueryResponse()
            }

            val result = instanceUnderTest.searchAvailablePhoneNumbers(input)

            result shouldNotBe null
            result.id shouldBe "mockSearchId"
            result.countryCode shouldBe "US"
            result.areaCode shouldBe "415"
            result.results.size shouldBe 1
            result.results.first().phoneNumber shouldBe "+14155550123"

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should pass the caller's criteria to the query`() =
        runTest {
            mockApiClient.stub {
                onBlocking { searchAvailablePhoneNumbersQuery(any()) } doReturn
                    DataFactory.searchAvailablePhoneNumbersQueryResponse()
            }

            instanceUnderTest.searchAvailablePhoneNumbers(input)

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(
                check {
                    it.countryCode shouldBe "US"
                    it.areaCode.getOrThrow() shouldBe "415"
                    it.limit.getOrThrow() shouldBe 5
                },
            )
        }

    @Test
    fun `searchAvailablePhoneNumbers() should omit the optional criteria the caller left unset`() =
        runTest {
            mockApiClient.stub {
                onBlocking { searchAvailablePhoneNumbersQuery(any()) } doReturn
                    DataFactory.searchAvailablePhoneNumbersQueryResponse()
            }

            instanceUnderTest.searchAvailablePhoneNumbers(
                SearchAvailablePhoneNumbersInput(countryCode = "CA"),
            )

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(
                check {
                    it.countryCode shouldBe "CA"
                    it.areaCode.getOrNull() shouldBe null
                    it.limit.getOrNull() shouldBe null
                },
            )
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw FailedException when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking { searchAvailablePhoneNumbersQuery(any()) } doReturn
                    DataFactory.searchAvailablePhoneNumbersEmptyResponse()
            }

            shouldThrow<PhoneNumberSearchException.FailedException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw AuthenticationException on a 401`() =
        runTest {
            stubError(mapOf(HTTP_STATUS_CODE_KEY to 401))

            shouldThrow<PhoneNumberSearchException.AuthenticationException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw FailedException on a 5xx`() =
        runTest {
            stubError(mapOf(HTTP_STATUS_CODE_KEY to 503))

            shouldThrow<PhoneNumberSearchException.FailedException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw UnsupportedCountryException`() =
        runTest {
            stubError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.UnsupportedCountry"),
            )

            shouldThrow<PhoneNumberSearchException.UnsupportedCountryException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw InvalidAreaCodeException`() =
        runTest {
            stubError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.InvalidAreaCode"),
            )

            shouldThrow<PhoneNumberSearchException.InvalidAreaCodeException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw LimitExceededException`() =
        runTest {
            stubError(mapOf(StringConstants.ERROR_TYPE to "sudoplatform.LimitExceededError"))

            shouldThrow<PhoneNumberSearchException.LimitExceededException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw InsufficientEntitlementsException`() =
        runTest {
            stubError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.InsufficientEntitlementsError"),
            )

            shouldThrow<PhoneNumberSearchException.InsufficientEntitlementsException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw InvalidArgumentException`() =
        runTest {
            stubError(mapOf(StringConstants.ERROR_TYPE to "sudoplatform.InvalidArgumentError"))

            shouldThrow<PhoneNumberSearchException.InvalidArgumentException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw IdentityContextMissingException`() =
        runTest {
            stubError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.IdentityContextMissing"),
            )

            shouldThrow<PhoneNumberSearchException.IdentityContextMissingException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw FailedException for an unrecognised error type`() =
        runTest {
            stubError(mapOf(StringConstants.ERROR_TYPE to "sudoplatform.SomethingElse"))

            shouldThrow<PhoneNumberSearchException.FailedException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw AuthenticationException when not authorized`() =
        runTest {
            mockApiClient.stub {
                onBlocking { searchAvailablePhoneNumbersQuery(any()) } doThrow
                    NotAuthorizedException("not authorized")
            }

            shouldThrow<PhoneNumberSearchException.AuthenticationException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    @Test
    fun `searchAvailablePhoneNumbers() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { searchAvailablePhoneNumbersQuery(any()) } doThrow
                    RuntimeException("network down")
            }

            shouldThrow<PhoneNumberSearchException.UnknownException> {
                instanceUnderTest.searchAvailablePhoneNumbers(input)
            }

            verify(mockApiClient).searchAvailablePhoneNumbersQuery(any())
        }

    private fun stubError(extensions: Map<String, Any>) {
        mockApiClient.stub {
            onBlocking { searchAvailablePhoneNumbersQuery(any()) } doReturn
                DataFactory.searchAvailablePhoneNumbersErrorResponse(
                    DataFactory.graphQLError(extensions = extensions),
                )
        }
    }
}
