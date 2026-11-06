/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.configuration

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient.ConfigurationDataException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudotelephony.internal.data.configuration.transformers.ConfigurationDataTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataService

/**
 * Implementation of [ConfigurationDataService] backed by the GraphQL API.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLConfigurationDataService(
    private val apiClient: ApiClient,
    private val logger: Logger,
) : ConfigurationDataService {
    override suspend fun getConfigurationData(): ConfigurationDataEntity {
        logger.debug("getTelephonyConfig")

        try {
            val queryResponse = apiClient.getTelephonyConfigQuery()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretConfigurationDataError(queryResponse.errors.first())
            }

            queryResponse.data?.let {
                return ConfigurationDataTransformer.fromGraphQLToEntity(it.getTelephonyConfig.telephonyConfigurationData)
            }
            logger.error("no configuration data returned")
            throw ConfigurationDataException.FailedException(
                StringConstants.NO_CONFIGURATION_DATA_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw ConfigurationDataException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretConfigurationDataException(e)
            }
        }
    }
}
