/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberSearchException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.transformers.PhoneNumberSearchTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.SearchAvailablePhoneNumbersInput
import com.sudoplatform.sudotelephony.graphql.type.SearchAvailablePhoneNumbersInput as SearchAvailablePhoneNumbersInputGraphQL

/**
 * Implementation of [PhoneNumberSearchService] backed by the GraphQL API.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLPhoneNumberSearchService(
    private val apiClient: ApiClient,
    private val logger: Logger,
) : PhoneNumberSearchService {
    override suspend fun searchAvailablePhoneNumbers(input: SearchAvailablePhoneNumbersInput): PhoneNumberSearchEntity {
        logger.debug("searchAvailablePhoneNumbers input: $input")

        val queryInput =
            SearchAvailablePhoneNumbersInputGraphQL(
                countryCode = input.countryCode,
                areaCode = Optional.presentIfNotNull(input.areaCode),
                limit = Optional.presentIfNotNull(input.limit),
            )

        try {
            val queryResponse = apiClient.searchAvailablePhoneNumbersQuery(queryInput)

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretPhoneNumberSearchError(queryResponse.errors.first())
            }

            val result = queryResponse.data?.searchAvailablePhoneNumbers?.phoneNumberSearch
            result?.let {
                return PhoneNumberSearchTransformer.fromGraphQLToEntity(it)
            }
            logger.error("no phone number search returned")
            throw PhoneNumberSearchException.FailedException(
                StringConstants.NO_PHONE_NUMBER_SEARCH_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw PhoneNumberSearchException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretPhoneNumberSearchException(e)
            }
        }
    }
}
