/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.country

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient.SupportedCountriesException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService

/**
 * Implementation of [CountryService] backed by the GraphQL API.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLCountryService(
    private val apiClient: ApiClient,
    private val logger: Logger,
) : CountryService {
    override suspend fun getSupportedCountries(): List<String> {
        logger.debug("getSupportedCountriesForTelephony")

        try {
            val queryResponse = apiClient.getSupportedCountriesForTelephonyQuery()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretSupportedCountriesError(queryResponse.errors.first())
            }

            val result = queryResponse.data
            result?.let {
                return it.getSupportedCountriesForTelephony
            }
            logger.error("no supported countries returned")
            throw SupportedCountriesException.FailedException(
                StringConstants.NO_SUPPORTED_COUNTRIES_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw SupportedCountriesException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretSupportedCountriesException(e)
            }
        }
    }
}
