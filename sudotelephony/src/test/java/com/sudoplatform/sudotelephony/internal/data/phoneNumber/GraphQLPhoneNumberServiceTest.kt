/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumber

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.data.DataFactory
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ListPhoneNumbersInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberStateEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ProvisionPhoneNumberRequest
import com.sudoplatform.sudotelephony.types.PhoneNumberState
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import com.sudoplatform.sudotelephony.graphql.type.KeyFormat as KeyFormatGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberFailureReason as PhoneNumberFailureReasonGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberState as PhoneNumberStateGraphQL

/**
 * Test the correct operation of [GraphQLPhoneNumberService]: the mutation carries the
 * caller's request and the public half of the key pair, the response fragment is transformed
 * to an entity, and GraphQL and throwable errors are interpreted into the expected
 * [PhoneNumberException] subclass.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLPhoneNumberServiceTest : BaseTests() {
    override val mockApiClient by before {
        mock<ApiClient>()
    }

    private val instanceUnderTest by before {
        GraphQLPhoneNumberService(
            mockApiClient,
            mockLogger,
        )
    }

    private val keyPair by before { EntityDataFactory.keyPair() }

    private val request by before {
        ProvisionPhoneNumberRequest(
            searchId = "mockSearchId",
            phoneNumber = "+14155550123",
            keyPair = keyPair,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockApiClient)
    }

    @Test
    fun `provision() should return the mapped entity when successful`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.provisionPhoneNumberMutationResponse()
            }

            val result = instanceUnderTest.provision(request)

            result shouldNotBe null
            result.id shouldBe "mockPhoneNumberId"
            result.phoneNumber shouldBe "+14155550123"
            result.keyRingId shouldBe "sudo-telephony.mockOwner"

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should send the search and number from the request`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.provisionPhoneNumberMutationResponse()
            }

            instanceUnderTest.provision(request)

            verify(mockApiClient).provisionPhoneNumberMutation(
                check {
                    it.searchId shouldBe "mockSearchId"
                    it.phoneNumber shouldBe "+14155550123"
                },
            )
        }

    @Test
    fun `provision() should send only the public half of the key pair`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.provisionPhoneNumberMutationResponse()
            }

            instanceUnderTest.provision(request)

            val encodedPublicKey = Base64.encodeAsString(*keyPair.publicKey)
            val encodedPrivateKey = Base64.encodeAsString(*keyPair.privateKey)

            verify(mockApiClient).provisionPhoneNumberMutation(
                check {
                    it.key.keyId shouldBe keyPair.keyId
                    it.key.algorithm shouldBe StringConstants.PUBLIC_KEY_ALGORITHM
                    it.key.publicKey shouldBe encodedPublicKey
                    // The private key must never reach the wire.
                    (it.key.publicKey == encodedPrivateKey) shouldBe false
                },
            )
        }

    @Test
    fun `provision() should declare the detected public key format`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.provisionPhoneNumberMutationResponse()
            }

            instanceUnderTest.provision(request)

            verify(mockApiClient).provisionPhoneNumberMutation(
                check {
                    // The fixture key pair comes from the JCE, whose encoding is X.509
                    // SubjectPublicKeyInfo. What matters is that whatever the detector reads
                    // from the bytes is the format declared to the service; the real device
                    // key store produces PKCS#1, which KeyArchiveTest asserts on a device.
                    it.key.keyFormat.getOrNull() shouldBe KeyFormatGraphQL.SPKI
                },
            )
        }

    @Test
    fun `provision() should omit the optional fields the caller left unset`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.provisionPhoneNumberMutationResponse()
            }

            instanceUnderTest.provision(request)

            verify(mockApiClient).provisionPhoneNumberMutation(
                check {
                    it.ownershipProofTokens.getOrNull() shouldBe null
                    it.clientRefId.getOrNull() shouldBe null
                },
            )
        }

    @Test
    fun `provision() should send exactly one ownership proof when supplied`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.provisionPhoneNumberMutationResponse()
            }

            instanceUnderTest.provision(
                request.copy(ownershipProofToken = "mockProof", clientRefId = "mockRef"),
            )

            verify(mockApiClient).provisionPhoneNumberMutation(
                check {
                    it.ownershipProofTokens.getOrNull() shouldBe listOf("mockProof")
                    it.clientRefId.getOrNull() shouldBe "mockRef"
                },
            )
        }

    @Test
    fun `provision() should map a failed number with its reason`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.provisionPhoneNumberMutationResponse(
                        DataFactory.phoneNumberFragment(
                            state = PhoneNumberStateGraphQL.FAILED,
                            failureReason = PhoneNumberFailureReasonGraphQL.CARRIER_ERROR,
                        ),
                    )
            }

            val result = instanceUnderTest.provision(request)

            result.state shouldBe PhoneNumberStateEntity.FAILED

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw ProvisionFailedException when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.provisionPhoneNumberEmptyResponse()
            }

            shouldThrow<PhoneNumberException.ProvisionFailedException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw AuthenticationException on a 401`() =
        runTest {
            stubError(mapOf(HTTP_STATUS_CODE_KEY to 401))

            shouldThrow<PhoneNumberException.AuthenticationException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw FailedException on a 5xx`() =
        runTest {
            stubError(mapOf(HTTP_STATUS_CODE_KEY to 503))

            shouldThrow<PhoneNumberException.FailedException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw PhoneNumberSearchExpiredException`() =
        runTest {
            stubError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.PhoneNumberSearchExpired"),
            )

            shouldThrow<PhoneNumberException.PhoneNumberSearchExpiredException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw PhoneNumberNotInSearchException`() =
        runTest {
            stubError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.PhoneNumberNotInSearch"),
            )

            shouldThrow<PhoneNumberException.PhoneNumberNotInSearchException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw PhoneNumberUnavailableException`() =
        runTest {
            stubError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.PhoneNumberUnavailable"),
            )

            shouldThrow<PhoneNumberException.PhoneNumberUnavailableException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw InvalidTokenException`() =
        runTest {
            stubError(mapOf(StringConstants.ERROR_TYPE to "sudoplatform.InvalidTokenError"))

            shouldThrow<PhoneNumberException.InvalidTokenException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw InsufficientEntitlementsException`() =
        runTest {
            stubError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.InsufficientEntitlementsError"),
            )

            shouldThrow<PhoneNumberException.InsufficientEntitlementsException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw IdentityContextMissingException`() =
        runTest {
            stubError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.IdentityContextMissing"),
            )

            shouldThrow<PhoneNumberException.IdentityContextMissingException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw InvalidArgumentException`() =
        runTest {
            stubError(mapOf(StringConstants.ERROR_TYPE to "sudoplatform.InvalidArgumentError"))

            shouldThrow<PhoneNumberException.InvalidArgumentException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw FailedException for an unrecognised error type`() =
        runTest {
            stubError(mapOf(StringConstants.ERROR_TYPE to "sudoplatform.SomethingElse"))

            shouldThrow<PhoneNumberException.FailedException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw AuthenticationException when not authorized`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doThrow
                    NotAuthorizedException("not authorized")
            }

            shouldThrow<PhoneNumberException.AuthenticationException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    @Test
    fun `provision() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { provisionPhoneNumberMutation(any()) } doThrow
                    RuntimeException("network down")
            }

            shouldThrow<PhoneNumberException.UnknownException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionPhoneNumberMutation(any())
        }

    private fun stubError(extensions: Map<String, Any>) {
        mockApiClient.stub {
            onBlocking { provisionPhoneNumberMutation(any()) } doReturn
                DataFactory.provisionPhoneNumberErrorResponse(
                    DataFactory.graphQLError(extensions = extensions),
                )
        }
    }

    @Test
    fun `deprovision() should return the released number when successful`() =
        runTest {
            mockApiClient.stub {
                onBlocking { deprovisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.deprovisionPhoneNumberMutationResponse()
            }

            val result = instanceUnderTest.deprovision("mockPhoneNumberId")

            result shouldNotBe null
            result.id shouldBe "mockPhoneNumberId"
            result.state shouldBe PhoneNumberStateEntity.RELEASED

            verify(mockApiClient).deprovisionPhoneNumberMutation(eq("mockPhoneNumberId"))
        }

    @Test
    fun `deprovision() should throw FailedException when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking { deprovisionPhoneNumberMutation(any()) } doReturn
                    DataFactory.deprovisionPhoneNumberEmptyResponse()
            }

            shouldThrow<PhoneNumberException.FailedException> {
                instanceUnderTest.deprovision("mockPhoneNumberId")
            }

            verify(mockApiClient).deprovisionPhoneNumberMutation(any())
        }

    @Test
    fun `deprovision() should throw PhoneNumberNotFoundException`() =
        runTest {
            stubDeprovisionError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.PhoneNumberNotFound"),
            )

            shouldThrow<PhoneNumberException.PhoneNumberNotFoundException> {
                instanceUnderTest.deprovision("mockPhoneNumberId")
            }

            verify(mockApiClient).deprovisionPhoneNumberMutation(any())
        }

    @Test
    fun `deprovision() should carry the state reported with an invalid-state error`() =
        runTest {
            stubDeprovisionError(
                mapOf(
                    StringConstants.ERROR_TYPE to "sudoplatform.telephony.InvalidPhoneNumberState",
                    StringConstants.ERROR_INFO to mapOf("state" to "PROVISIONING"),
                ),
            )

            val exception =
                shouldThrow<PhoneNumberException.InvalidPhoneNumberStateException> {
                    instanceUnderTest.deprovision("mockPhoneNumberId")
                }
            exception.state shouldBe PhoneNumberState.PROVISIONING

            verify(mockApiClient).deprovisionPhoneNumberMutation(any())
        }

    @Test
    fun `deprovision() should leave the state null when the service reports none`() =
        runTest {
            stubDeprovisionError(
                mapOf(StringConstants.ERROR_TYPE to "sudoplatform.telephony.InvalidPhoneNumberState"),
            )

            val exception =
                shouldThrow<PhoneNumberException.InvalidPhoneNumberStateException> {
                    instanceUnderTest.deprovision("mockPhoneNumberId")
                }
            exception.state shouldBe null

            verify(mockApiClient).deprovisionPhoneNumberMutation(any())
        }

    @Test
    fun `deprovision() should map an unrecognised reported state to UNKNOWN`() =
        runTest {
            stubDeprovisionError(
                mapOf(
                    StringConstants.ERROR_TYPE to "sudoplatform.telephony.InvalidPhoneNumberState",
                    StringConstants.ERROR_INFO to mapOf("state" to "SOMETHING_NEW"),
                ),
            )

            val exception =
                shouldThrow<PhoneNumberException.InvalidPhoneNumberStateException> {
                    instanceUnderTest.deprovision("mockPhoneNumberId")
                }
            exception.state shouldBe PhoneNumberState.UNKNOWN

            verify(mockApiClient).deprovisionPhoneNumberMutation(any())
        }

    @Test
    fun `deprovision() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { deprovisionPhoneNumberMutation(any()) } doThrow
                    RuntimeException("network down")
            }

            shouldThrow<PhoneNumberException.UnknownException> {
                instanceUnderTest.deprovision("mockPhoneNumberId")
            }

            verify(mockApiClient).deprovisionPhoneNumberMutation(any())
        }

    private fun stubDeprovisionError(extensions: Map<String, Any>) {
        mockApiClient.stub {
            onBlocking { deprovisionPhoneNumberMutation(any()) } doReturn
                DataFactory.deprovisionPhoneNumberErrorResponse(
                    DataFactory.graphQLError(extensions = extensions),
                )
        }
    }

    @Test
    fun `get() should return the mapped entity when the number exists`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getPhoneNumberQuery(any()) } doReturn
                    DataFactory.getPhoneNumberQueryResponse()
            }

            val result = instanceUnderTest.get("mockPhoneNumberId")

            result shouldNotBe null
            result!!.id shouldBe "mockPhoneNumberId"
            result.phoneNumber shouldBe "+14155550123"

            verify(mockApiClient).getPhoneNumberQuery(eq("mockPhoneNumberId"))
        }

    @Test
    fun `get() should return null when no number with the id belongs to the caller`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getPhoneNumberQuery(any()) } doReturn
                    DataFactory.getPhoneNumberQueryResponse(number = null)
            }

            instanceUnderTest.get("mockPhoneNumberId") shouldBe null

            verify(mockApiClient).getPhoneNumberQuery(any())
        }

    @Test
    fun `get() should throw InvalidArgumentException on a bad argument`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getPhoneNumberQuery(any()) } doReturn
                    DataFactory.getPhoneNumberErrorResponse(
                        DataFactory.graphQLError(
                            extensions = mapOf(StringConstants.ERROR_TYPE to "sudoplatform.InvalidArgumentError"),
                        ),
                    )
            }

            shouldThrow<PhoneNumberException.InvalidArgumentException> {
                instanceUnderTest.get("mockPhoneNumberId")
            }

            verify(mockApiClient).getPhoneNumberQuery(any())
        }

    @Test
    fun `get() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getPhoneNumberQuery(any()) } doThrow RuntimeException("network down")
            }

            shouldThrow<PhoneNumberException.UnknownException> {
                instanceUnderTest.get("mockPhoneNumberId")
            }

            verify(mockApiClient).getPhoneNumberQuery(any())
        }

    @Test
    fun `list() should return the mapped page and its next token`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneNumbersQuery(any()) } doReturn
                    DataFactory.listPhoneNumbersQueryResponse(
                        numbers =
                            listOf(
                                DataFactory.phoneNumberFragment(id = "first"),
                                DataFactory.phoneNumberFragment(id = "second"),
                            ),
                        nextToken = "mockNextToken",
                    )
            }

            val result = instanceUnderTest.list(ListPhoneNumbersInput())

            result.items.size shouldBe 2
            result.items.map { it.id } shouldBe listOf("first", "second")
            result.nextToken shouldBe "mockNextToken"

            verify(mockApiClient).listPhoneNumbersQuery(any())
        }

    @Test
    fun `list() should return an empty page with no next token`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneNumbersQuery(any()) } doReturn
                    DataFactory.listPhoneNumbersQueryResponse(numbers = emptyList())
            }

            val result = instanceUnderTest.list(ListPhoneNumbersInput())

            result.items shouldBe emptyList()
            result.nextToken shouldBe null

            verify(mockApiClient).listPhoneNumbersQuery(any())
        }

    @Test
    fun `list() should send the caller's paging parameters`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneNumbersQuery(any()) } doReturn
                    DataFactory.listPhoneNumbersQueryResponse()
            }

            instanceUnderTest.list(ListPhoneNumbersInput(limit = 5, nextToken = "token"))

            verify(mockApiClient).listPhoneNumbersQuery(
                check {
                    it.limit.getOrNull() shouldBe 5
                    it.nextToken.getOrNull() shouldBe "token"
                },
            )
        }

    @Test
    fun `list() should omit the paging parameters the caller left unset`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneNumbersQuery(any()) } doReturn
                    DataFactory.listPhoneNumbersQueryResponse()
            }

            instanceUnderTest.list(ListPhoneNumbersInput())

            verify(mockApiClient).listPhoneNumbersQuery(
                check {
                    it.limit.getOrNull() shouldBe null
                    it.nextToken.getOrNull() shouldBe null
                },
            )
        }

    @Test
    fun `list() should throw FailedException when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneNumbersQuery(any()) } doReturn
                    DataFactory.listPhoneNumbersEmptyResponse()
            }

            shouldThrow<PhoneNumberException.FailedException> {
                instanceUnderTest.list(ListPhoneNumbersInput())
            }

            verify(mockApiClient).listPhoneNumbersQuery(any())
        }

    @Test
    fun `list() should throw InvalidArgumentException for a token the service rejects`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneNumbersQuery(any()) } doReturn
                    DataFactory.listPhoneNumbersErrorResponse(
                        DataFactory.graphQLError(
                            extensions = mapOf(StringConstants.ERROR_TYPE to "sudoplatform.InvalidArgumentError"),
                        ),
                    )
            }

            shouldThrow<PhoneNumberException.InvalidArgumentException> {
                instanceUnderTest.list(ListPhoneNumbersInput(nextToken = "bogus"))
            }

            verify(mockApiClient).listPhoneNumbersQuery(any())
        }

    @Test
    fun `list() should throw UnknownException for unexpected errors`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listPhoneNumbersQuery(any()) } doThrow RuntimeException("network down")
            }

            shouldThrow<PhoneNumberException.UnknownException> {
                instanceUnderTest.list(ListPhoneNumbersInput())
            }

            verify(mockApiClient).listPhoneNumbersQuery(any())
        }
}
