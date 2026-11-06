/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.configuration

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.ConfigurationDataException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.data.DataFactory
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudouser.exceptions.HTTP_STATUS_CODE_KEY
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
 * Test the correct operation of [GraphQLConfigurationDataService]: the right query is
 * executed, the response is transformed to an entity, and GraphQL and throwable errors are
 * interpreted into the expected [ConfigurationDataException] subclass.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLConfigurationDataServiceTest : BaseTests() {
    override val mockApiClient by before {
        mock<ApiClient>()
    }

    private val instanceUnderTest by before {
        GraphQLConfigurationDataService(
            mockApiClient,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockApiClient)
    }

    private fun stubError(extensions: Map<String, Any>) {
        mockApiClient.stub {
            onBlocking { getTelephonyConfigQuery() } doReturn
                DataFactory.getTelephonyConfigErrorResponse(DataFactory.graphQLError(extensions = extensions))
        }
    }

    @Test
    fun `getConfigurationData() should return the configuration when successful`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getTelephonyConfigQuery() } doReturn DataFactory.getTelephonyConfigQueryResponse()
            }

            instanceUnderTest.getConfigurationData() shouldBe EntityDataFactory.configurationDataEntity()

            verify(mockApiClient).getTelephonyConfigQuery()
        }

    @Test
    fun `getConfigurationData() should map a deployment that does not require a Sudo proof`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getTelephonyConfigQuery() } doReturn
                    DataFactory.getTelephonyConfigQueryResponse(sudoOwnershipProofRequired = false)
            }

            instanceUnderTest.getConfigurationData().sudoOwnershipProofRequired shouldBe false

            verify(mockApiClient).getTelephonyConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw FailedException when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getTelephonyConfigQuery() } doReturn DataFactory.getTelephonyConfigEmptyResponse()
            }

            shouldThrow<ConfigurationDataException.FailedException> {
                instanceUnderTest.getConfigurationData()
            }

            verify(mockApiClient).getTelephonyConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw AuthenticationException on a 401`() =
        runTest {
            stubError(mapOf(HTTP_STATUS_CODE_KEY to 401))

            shouldThrow<ConfigurationDataException.AuthenticationException> {
                instanceUnderTest.getConfigurationData()
            }

            verify(mockApiClient).getTelephonyConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw FailedException on a 5xx`() =
        runTest {
            stubError(mapOf(HTTP_STATUS_CODE_KEY to 503))

            shouldThrow<ConfigurationDataException.FailedException> {
                instanceUnderTest.getConfigurationData()
            }

            verify(mockApiClient).getTelephonyConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw IdentityContextMissingException`() =
        runTest {
            stubError(mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.IdentityContextMissing"))

            shouldThrow<ConfigurationDataException.IdentityContextMissingException> {
                instanceUnderTest.getConfigurationData()
            }

            verify(mockApiClient).getTelephonyConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw ServiceException`() =
        runTest {
            stubError(mapOf(StringConstants.ERROR_TYPE to "sudoplatform.ServiceError"))

            shouldThrow<ConfigurationDataException.ServiceException> {
                instanceUnderTest.getConfigurationData()
            }

            verify(mockApiClient).getTelephonyConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw FailedException for an unrecognised error type`() =
        runTest {
            stubError(mapOf(StringConstants.ERROR_TYPE to "sudoplatform.SomethingElse"))

            shouldThrow<ConfigurationDataException.FailedException> {
                instanceUnderTest.getConfigurationData()
            }

            verify(mockApiClient).getTelephonyConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw AuthenticationException when not authorized`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getTelephonyConfigQuery() } doThrow NotAuthorizedException("not authorized")
            }

            shouldThrow<ConfigurationDataException.AuthenticationException> {
                instanceUnderTest.getConfigurationData()
            }

            verify(mockApiClient).getTelephonyConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getTelephonyConfigQuery() } doThrow RuntimeException("network down")
            }

            shouldThrow<ConfigurationDataException.UnknownException> {
                instanceUnderTest.getConfigurationData()
            }

            verify(mockApiClient).getTelephonyConfigQuery()
        }
}
