/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.country

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.SupportedCountriesException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.data.DataFactory
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudouser.exceptions.HTTP_STATUS_CODE_KEY
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
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
 * Test the correct operation of [GraphQLCountryService]: the right query is executed, the
 * response is transformed to an entity, and GraphQL and throwable errors are interpreted
 * into the expected [SupportedCountriesException] subclass.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLCountryServiceTest : BaseTests() {
    override val mockApiClient by before {
        mock<ApiClient>()
    }

    private val instanceUnderTest by before {
        GraphQLCountryService(
            mockApiClient,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockApiClient)
    }

    @Test
    fun `getSupportedCountries() should return the country codes when successful`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyQueryResponse()
            }

            val result = instanceUnderTest.getSupportedCountries()

            result shouldNotBe null
            result shouldBe EntityDataFactory.countryCodes

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should return an empty list when the service supports no countries`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyQueryResponse(emptyList())
            }

            instanceUnderTest.getSupportedCountries() shouldBe emptyList()

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw FailedException when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyEmptyResponse()
            }

            shouldThrow<SupportedCountriesException.FailedException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw AuthenticationException on a 401`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyErrorResponse(
                        DataFactory.graphQLError(
                            message = "unauthorized",
                            extensions = mapOf(HTTP_STATUS_CODE_KEY to 401),
                        ),
                    )
            }

            shouldThrow<SupportedCountriesException.AuthenticationException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw FailedException on a 5xx`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyErrorResponse(
                        DataFactory.graphQLError(
                            message = "internal error",
                            extensions = mapOf(HTTP_STATUS_CODE_KEY to 503),
                        ),
                    )
            }

            shouldThrow<SupportedCountriesException.FailedException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw SupportedCountriesUnavailableException`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyErrorResponse(
                        DataFactory.graphQLError(
                            extensions =
                                mapOf(
                                    StringConstants.ERROR_TYPE to
                                        "sudoplatform.telephony.SupportedCountriesUnavailable",
                                ),
                        ),
                    )
            }

            shouldThrow<SupportedCountriesException.SupportedCountriesUnavailableException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw InvalidArgumentException`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyErrorResponse(
                        DataFactory.graphQLError(
                            extensions =
                                mapOf(
                                    StringConstants.ERROR_TYPE to "sudoplatform.InvalidArgumentError",
                                ),
                        ),
                    )
            }

            shouldThrow<SupportedCountriesException.InvalidArgumentException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw IdentityContextMissingException`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyErrorResponse(
                        DataFactory.graphQLError(
                            extensions =
                                mapOf(
                                    StringConstants.ERROR_TYPE to
                                        "sudoplatform.telephony.IdentityContextMissing",
                                ),
                        ),
                    )
            }

            shouldThrow<SupportedCountriesException.IdentityContextMissingException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw ServiceException`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyErrorResponse(
                        DataFactory.graphQLError(
                            extensions = mapOf(StringConstants.ERROR_TYPE to "sudoplatform.ServiceError"),
                        ),
                    )
            }

            shouldThrow<SupportedCountriesException.ServiceException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw FailedException for an unrecognised error type`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doReturn
                    DataFactory.getSupportedCountriesForTelephonyErrorResponse(
                        DataFactory.graphQLError(
                            extensions = mapOf(StringConstants.ERROR_TYPE to "sudoplatform.SomethingElse"),
                        ),
                    )
            }

            shouldThrow<SupportedCountriesException.FailedException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw AuthenticationException when not authorized`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doThrow
                    NotAuthorizedException("not authorized")
            }

            shouldThrow<SupportedCountriesException.AuthenticationException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }

    @Test
    fun `getSupportedCountries() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getSupportedCountriesForTelephonyQuery() } doThrow
                    RuntimeException("network down")
            }

            shouldThrow<SupportedCountriesException.UnknownException> {
                instanceUnderTest.getSupportedCountries()
            }

            verify(mockApiClient).getSupportedCountriesForTelephonyQuery()
        }
}
