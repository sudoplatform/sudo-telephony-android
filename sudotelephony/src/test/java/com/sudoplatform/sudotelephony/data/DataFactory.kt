/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.data

import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudotelephony.graphql.DeletePhoneMessagesMutation
import com.sudoplatform.sudotelephony.graphql.DeprovisionPhoneNumberMutation
import com.sudoplatform.sudotelephony.graphql.GetPhoneMessageQuery
import com.sudoplatform.sudotelephony.graphql.GetPhoneNumberQuery
import com.sudoplatform.sudotelephony.graphql.GetSupportedCountriesForTelephonyQuery
import com.sudoplatform.sudotelephony.graphql.ListPhoneMessagesQuery
import com.sudoplatform.sudotelephony.graphql.ListPhoneNumbersQuery
import com.sudoplatform.sudotelephony.graphql.ProvisionPhoneNumberMutation
import com.sudoplatform.sudotelephony.graphql.SearchAvailablePhoneNumbersQuery
import com.sudoplatform.sudotelephony.graphql.UpdatePhoneMessagesMutation
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageDirection
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageType
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberFailureReason
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberState
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberType
import com.sudoplatform.sudotelephony.graphql.fragment.AvailablePhoneNumber as AvailablePhoneNumberFragment
import com.sudoplatform.sudotelephony.graphql.fragment.PhoneNumber as PhoneNumberFragment
import com.sudoplatform.sudotelephony.graphql.fragment.PhoneNumberSearch as PhoneNumberSearchFragment
import com.sudoplatform.sudotelephony.graphql.fragment.SealedPhoneMessage as SealedPhoneMessageFragment

/**
 * Builders for consistent GraphQL-wire fixtures (responses and errors) used across unit
 * tests.
 */
internal object DataFactory {
    /** Build a successful response for `GetSupportedCountriesForTelephony`. */
    fun getSupportedCountriesForTelephonyQueryResponse(
        codes: List<String> = EntityDataFactory.countryCodes,
    ): GraphQLResponse<GetSupportedCountriesForTelephonyQuery.Data> =
        GraphQLResponse(
            GetSupportedCountriesForTelephonyQuery.Data(getSupportedCountriesForTelephony = codes),
            null,
        )

    /** Build an error response for `GetSupportedCountriesForTelephony`. */
    fun getSupportedCountriesForTelephonyErrorResponse(
        error: GraphQLResponse.Error,
    ): GraphQLResponse<GetSupportedCountriesForTelephonyQuery.Data> = GraphQLResponse(null, listOf(error))

    /** Build a response for `GetSupportedCountriesForTelephony` carrying neither data nor errors. */
    fun getSupportedCountriesForTelephonyEmptyResponse(): GraphQLResponse<GetSupportedCountriesForTelephonyQuery.Data> =
        GraphQLResponse(null, null)

    /** Build an [AvailablePhoneNumberFragment] fixture. */
    fun availablePhoneNumberFragment(
        phoneNumber: String = "+14155550123",
        countryCode: String = "US",
        numberType: PhoneNumberType = PhoneNumberType.LOCAL,
        locality: String? = "San Francisco",
        region: String? = "CA",
    ) = AvailablePhoneNumberFragment(
        phoneNumber = phoneNumber,
        countryCode = countryCode,
        numberType = numberType,
        locality = locality,
        region = region,
    )

    /** Build a [PhoneNumberSearchFragment] fixture. */
    fun phoneNumberSearchFragment(
        id: String = "mockSearchId",
        countryCode: String = "US",
        areaCode: String? = "415",
        results: List<AvailablePhoneNumberFragment> = listOf(availablePhoneNumberFragment()),
        expiresAtEpochMs: Double = EntityDataFactory.EXPIRES_AT_EPOCH_MS,
        owner: String = "mockOwner",
        version: Int = 1,
        createdAtEpochMs: Double = EntityDataFactory.CREATED_AT_EPOCH_MS,
        updatedAtEpochMs: Double = EntityDataFactory.UPDATED_AT_EPOCH_MS,
    ) = PhoneNumberSearchFragment(
        id = id,
        countryCode = countryCode,
        areaCode = areaCode,
        results =
            results.map {
                PhoneNumberSearchFragment.Result(
                    __typename = "AvailablePhoneNumber",
                    availablePhoneNumber = it,
                )
            },
        expiresAtEpochMs = expiresAtEpochMs,
        owner = owner,
        version = version,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    /** Build a successful response for `SearchAvailablePhoneNumbers`. */
    fun searchAvailablePhoneNumbersQueryResponse(
        search: PhoneNumberSearchFragment = phoneNumberSearchFragment(),
    ): GraphQLResponse<SearchAvailablePhoneNumbersQuery.Data> =
        GraphQLResponse(
            SearchAvailablePhoneNumbersQuery.Data(
                searchAvailablePhoneNumbers =
                    SearchAvailablePhoneNumbersQuery.SearchAvailablePhoneNumbers(
                        __typename = "PhoneNumberSearch",
                        phoneNumberSearch = search,
                    ),
            ),
            null,
        )

    /** Build an error response for `SearchAvailablePhoneNumbers`. */
    fun searchAvailablePhoneNumbersErrorResponse(error: GraphQLResponse.Error): GraphQLResponse<SearchAvailablePhoneNumbersQuery.Data> =
        GraphQLResponse(null, listOf(error))

    /** Build a response for `SearchAvailablePhoneNumbers` carrying neither data nor errors. */
    fun searchAvailablePhoneNumbersEmptyResponse(): GraphQLResponse<SearchAvailablePhoneNumbersQuery.Data> = GraphQLResponse(null, null)

    /** Build a GraphQL error carrying the given [message] and [extensions]. */
    fun graphQLError(
        message: String = "Test generated error",
        extensions: Map<String, Any> = emptyMap(),
    ): GraphQLResponse.Error = GraphQLResponse.Error(message, emptyList(), emptyList(), extensions)

    /** Build a [PhoneNumberFragment] fixture. */
    fun phoneNumberFragment(
        id: String = "mockPhoneNumberId",
        owner: String = "mockOwner",
        owners: List<Pair<String, String>> = listOf("mockSudoId" to "sudoplatform.sudoservice"),
        identityId: String = "mockIdentityId",
        keyRingId: String = "sudo-telephony.mockOwner",
        keyIds: List<String> = listOf("mockKeyId"),
        phoneNumber: String = "+14155550123",
        countryCode: String = "US",
        numberType: PhoneNumberType = PhoneNumberType.LOCAL,
        areaCode: String? = "415",
        state: PhoneNumberState = PhoneNumberState.PROVISIONING,
        failureReason: PhoneNumberFailureReason? = null,
        gracePeriodEndsAtEpochMs: Double? = null,
        clientRefId: String? = null,
        version: Int = 1,
        createdAtEpochMs: Double = EntityDataFactory.CREATED_AT_EPOCH_MS,
        updatedAtEpochMs: Double = EntityDataFactory.UPDATED_AT_EPOCH_MS,
    ) = PhoneNumberFragment(
        id = id,
        owner = owner,
        owners = owners.map { PhoneNumberFragment.Owner(id = it.first, issuer = it.second) },
        identityId = identityId,
        keyRingId = keyRingId,
        keyIds = keyIds,
        phoneNumber = phoneNumber,
        countryCode = countryCode,
        numberType = numberType,
        areaCode = areaCode,
        state = state,
        failureReason = failureReason,
        gracePeriodEndsAtEpochMs = gracePeriodEndsAtEpochMs,
        clientRefId = clientRefId,
        version = version,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    /** Build a successful response for `ProvisionPhoneNumber`. */
    fun provisionPhoneNumberMutationResponse(
        number: PhoneNumberFragment = phoneNumberFragment(),
    ): GraphQLResponse<ProvisionPhoneNumberMutation.Data> =
        GraphQLResponse(
            ProvisionPhoneNumberMutation.Data(
                provisionPhoneNumber =
                    ProvisionPhoneNumberMutation.ProvisionPhoneNumber(
                        __typename = "PhoneNumber",
                        phoneNumber = number,
                    ),
            ),
            null,
        )

    /** Build an error response for `ProvisionPhoneNumber`. */
    fun provisionPhoneNumberErrorResponse(error: GraphQLResponse.Error): GraphQLResponse<ProvisionPhoneNumberMutation.Data> =
        GraphQLResponse(null, listOf(error))

    /** Build a response for `ProvisionPhoneNumber` carrying neither data nor errors. */
    fun provisionPhoneNumberEmptyResponse(): GraphQLResponse<ProvisionPhoneNumberMutation.Data> = GraphQLResponse(null, null)

    /** Build a successful response for `DeprovisionPhoneNumber`. */
    fun deprovisionPhoneNumberMutationResponse(
        number: PhoneNumberFragment = phoneNumberFragment(state = PhoneNumberState.RELEASED, version = 2),
    ): GraphQLResponse<DeprovisionPhoneNumberMutation.Data> =
        GraphQLResponse(
            DeprovisionPhoneNumberMutation.Data(
                deprovisionPhoneNumber =
                    DeprovisionPhoneNumberMutation.DeprovisionPhoneNumber(
                        __typename = "PhoneNumber",
                        phoneNumber = number,
                    ),
            ),
            null,
        )

    /** Build an error response for `DeprovisionPhoneNumber`. */
    fun deprovisionPhoneNumberErrorResponse(error: GraphQLResponse.Error): GraphQLResponse<DeprovisionPhoneNumberMutation.Data> =
        GraphQLResponse(null, listOf(error))

    /** Build a response for `DeprovisionPhoneNumber` carrying neither data nor errors. */
    fun deprovisionPhoneNumberEmptyResponse(): GraphQLResponse<DeprovisionPhoneNumberMutation.Data> = GraphQLResponse(null, null)

    /** Build a successful response for `GetPhoneNumber`. */
    fun getPhoneNumberQueryResponse(number: PhoneNumberFragment? = phoneNumberFragment()): GraphQLResponse<GetPhoneNumberQuery.Data> =
        GraphQLResponse(
            GetPhoneNumberQuery.Data(
                getPhoneNumber =
                    number?.let {
                        GetPhoneNumberQuery.GetPhoneNumber(
                            __typename = "PhoneNumber",
                            phoneNumber = it,
                        )
                    },
            ),
            null,
        )

    /** Build an error response for `GetPhoneNumber`. */
    fun getPhoneNumberErrorResponse(error: GraphQLResponse.Error): GraphQLResponse<GetPhoneNumberQuery.Data> =
        GraphQLResponse(null, listOf(error))

    /** Build a successful response for `ListPhoneNumbers`. */
    fun listPhoneNumbersQueryResponse(
        numbers: List<PhoneNumberFragment> = listOf(phoneNumberFragment()),
        nextToken: String? = null,
    ): GraphQLResponse<ListPhoneNumbersQuery.Data> =
        GraphQLResponse(
            ListPhoneNumbersQuery.Data(
                listPhoneNumbers =
                    ListPhoneNumbersQuery.ListPhoneNumbers(
                        items =
                            numbers.map {
                                ListPhoneNumbersQuery.Item(
                                    __typename = "PhoneNumber",
                                    phoneNumber = it,
                                )
                            },
                        nextToken = nextToken,
                    ),
            ),
            null,
        )

    /** Build an error response for `ListPhoneNumbers`. */
    fun listPhoneNumbersErrorResponse(error: GraphQLResponse.Error): GraphQLResponse<ListPhoneNumbersQuery.Data> =
        GraphQLResponse(null, listOf(error))

    /** Build a response for `ListPhoneNumbers` carrying neither data nor errors. */
    fun listPhoneNumbersEmptyResponse(): GraphQLResponse<ListPhoneNumbersQuery.Data> = GraphQLResponse(null, null)

    /** Build a [SealedPhoneMessageFragment] fixture. */
    fun sealedPhoneMessageFragment(
        id: String = "mockMessageId",
        phoneNumberId: String = "mockPhoneNumberId",
        conversationId: String = "mockConversationId",
        remotePhoneNumber: String? = EntityDataFactory.SEALED_REMOTE_NUMBER,
        body: String = EntityDataFactory.SEALED_BODY,
        seen: Boolean = false,
    ) = SealedPhoneMessageFragment(
        id = id,
        owner = "mockOwner",
        owners = listOf(SealedPhoneMessageFragment.Owner(id = "mockSudoId", issuer = "sudoplatform.sudoservice")),
        phoneNumberId = phoneNumberId,
        conversationId = conversationId,
        type = PhoneMessageType.SMS,
        direction = PhoneMessageDirection.INBOUND,
        localPhoneNumber = "+14155550123",
        remotePhoneNumber = remotePhoneNumber,
        body = body,
        segmentCount = 1,
        mediaOmitted = false,
        seen = seen,
        receivedAtEpochMs = EntityDataFactory.CREATED_AT_EPOCH_MS,
        version = 1,
        createdAtEpochMs = EntityDataFactory.CREATED_AT_EPOCH_MS,
        updatedAtEpochMs = EntityDataFactory.UPDATED_AT_EPOCH_MS,
    )

    /** Build a successful response for `GetPhoneMessage`. */
    fun getPhoneMessageQueryResponse(
        message: SealedPhoneMessageFragment? = sealedPhoneMessageFragment(),
    ): GraphQLResponse<GetPhoneMessageQuery.Data> =
        GraphQLResponse(
            GetPhoneMessageQuery.Data(
                getPhoneMessage =
                    message?.let {
                        GetPhoneMessageQuery.GetPhoneMessage(
                            __typename = "SealedPhoneMessage",
                            sealedPhoneMessage = it,
                        )
                    },
            ),
            null,
        )

    /** Build an error response for `GetPhoneMessage`. */
    fun getPhoneMessageErrorResponse(error: GraphQLResponse.Error): GraphQLResponse<GetPhoneMessageQuery.Data> =
        GraphQLResponse(null, listOf(error))

    /** Build a successful response for `ListPhoneMessages`. */
    fun listPhoneMessagesQueryResponse(
        messages: List<SealedPhoneMessageFragment> = listOf(sealedPhoneMessageFragment()),
        nextToken: String? = null,
    ): GraphQLResponse<ListPhoneMessagesQuery.Data> =
        GraphQLResponse(
            ListPhoneMessagesQuery.Data(
                listPhoneMessages =
                    ListPhoneMessagesQuery.ListPhoneMessages(
                        items =
                            messages.map {
                                ListPhoneMessagesQuery.Item(
                                    __typename = "SealedPhoneMessage",
                                    sealedPhoneMessage = it,
                                )
                            },
                        nextToken = nextToken,
                    ),
            ),
            null,
        )

    /** Build an error response for `ListPhoneMessages`. */
    fun listPhoneMessagesErrorResponse(error: GraphQLResponse.Error): GraphQLResponse<ListPhoneMessagesQuery.Data> =
        GraphQLResponse(null, listOf(error))

    /** Build a response for `ListPhoneMessages` carrying neither data nor errors. */
    fun listPhoneMessagesEmptyResponse(): GraphQLResponse<ListPhoneMessagesQuery.Data> = GraphQLResponse(null, null)

    /** Build a successful response for `DeletePhoneMessages`. */
    fun deletePhoneMessagesMutationResponse(
        succeededIds: List<String> = listOf("mockMessageId"),
        failedIds: List<String> = emptyList(),
    ): GraphQLResponse<DeletePhoneMessagesMutation.Data> =
        GraphQLResponse(
            DeletePhoneMessagesMutation.Data(
                deletePhoneMessages =
                    DeletePhoneMessagesMutation.DeletePhoneMessages(
                        succeededIds = succeededIds,
                        failedIds = failedIds,
                    ),
            ),
            null,
        )

    /** Build an error response for `DeletePhoneMessages`. */
    fun deletePhoneMessagesErrorResponse(error: GraphQLResponse.Error): GraphQLResponse<DeletePhoneMessagesMutation.Data> =
        GraphQLResponse(null, listOf(error))

    /** Build a response for `DeletePhoneMessages` carrying neither data nor errors. */
    fun deletePhoneMessagesEmptyResponse(): GraphQLResponse<DeletePhoneMessagesMutation.Data> = GraphQLResponse(null, null)

    /** Build a successful response for `UpdatePhoneMessages`. */
    fun updatePhoneMessagesMutationResponse(
        succeededIds: List<String> = listOf("mockMessageId"),
        failedIds: List<String> = emptyList(),
    ): GraphQLResponse<UpdatePhoneMessagesMutation.Data> =
        GraphQLResponse(
            UpdatePhoneMessagesMutation.Data(
                updatePhoneMessages =
                    UpdatePhoneMessagesMutation.UpdatePhoneMessages(
                        succeededIds = succeededIds,
                        failedIds = failedIds,
                    ),
            ),
            null,
        )

    /** Build an error response for `UpdatePhoneMessages`. */
    fun updatePhoneMessagesErrorResponse(error: GraphQLResponse.Error): GraphQLResponse<UpdatePhoneMessagesMutation.Data> =
        GraphQLResponse(null, listOf(error))
}
