/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumber

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudotelephony.internal.data.common.transformers.PublicKeyFormatTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.PhoneNumberTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ListPhoneNumbersOutput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ProvisionPhoneNumberRequest
import com.sudoplatform.sudotelephony.graphql.type.ListPhoneNumbersInput as ListPhoneNumbersInputGraphQL
import com.sudoplatform.sudotelephony.graphql.type.ProvisionPhoneNumberInput as ProvisionPhoneNumberInputGraphQL
import com.sudoplatform.sudotelephony.graphql.type.ProvisionPhoneNumberPublicKeyInput as PublicKeyInputGraphQL
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ListPhoneNumbersInput as ListPhoneNumbersInputEntity

/**
 * Implementation of [PhoneNumberService] backed by the GraphQL API.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing operations.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLPhoneNumberService(
    private val apiClient: ApiClient,
    private val logger: Logger,
) : PhoneNumberService {
    override suspend fun provision(request: ProvisionPhoneNumberRequest): PhoneNumberEntity {
        logger.debug("provisionPhoneNumber phoneNumber: ${request.phoneNumber}")

        // Only the public half of the key pair is sent; the private key stays on the device.
        val keyInput =
            PublicKeyInputGraphQL(
                keyId = request.keyPair.keyId,
                algorithm = StringConstants.PUBLIC_KEY_ALGORITHM,
                keyFormat =
                    Optional.presentIfNotNull(
                        PublicKeyFormatTransformer.fromEntityToGraphQL(request.keyPair.getKeyFormat()),
                    ),
                publicKey = Base64.encodeAsString(*request.keyPair.publicKey),
            )

        // The service requires exactly one ownership proof when any is supplied, so an
        // absent token is omitted rather than sent as an empty list.
        val mutationInput =
            ProvisionPhoneNumberInputGraphQL(
                searchId = request.searchId,
                phoneNumber = request.phoneNumber,
                key = keyInput,
                ownershipProofTokens =
                    Optional.presentIfNotNull(
                        request.ownershipProofToken?.let { listOf(it) },
                    ),
                clientRefId = Optional.presentIfNotNull(request.clientRefId),
            )

        try {
            val mutationResponse = apiClient.provisionPhoneNumberMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretPhoneNumberError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.provisionPhoneNumber?.phoneNumber
            result?.let {
                return PhoneNumberTransformer.fromGraphQLToEntity(it)
            }
            logger.error("no phone number returned")
            throw PhoneNumberException.ProvisionFailedException(
                StringConstants.NO_PHONE_NUMBER_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw PhoneNumberException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretPhoneNumberException(e)
            }
        }
    }

    override suspend fun deprovision(id: String): PhoneNumberEntity {
        logger.debug("deprovisionPhoneNumber id: $id")

        try {
            val mutationResponse = apiClient.deprovisionPhoneNumberMutation(id)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretPhoneNumberError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.deprovisionPhoneNumber?.phoneNumber
            result?.let {
                return PhoneNumberTransformer.fromGraphQLToEntity(it)
            }
            logger.error("no phone number returned")
            throw PhoneNumberException.FailedException(
                StringConstants.NO_PHONE_NUMBER_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw PhoneNumberException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretPhoneNumberException(e)
            }
        }
    }

    override suspend fun get(id: String): PhoneNumberEntity? {
        logger.debug("getPhoneNumber id: $id")

        try {
            val queryResponse = apiClient.getPhoneNumberQuery(id)

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretPhoneNumberError(queryResponse.errors.first())
            }

            // The service returns null when no number with the id belongs to the caller.
            val result = queryResponse.data?.getPhoneNumber ?: return null
            return PhoneNumberTransformer.fromGraphQLToEntity(result.phoneNumber)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw PhoneNumberException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretPhoneNumberException(e)
            }
        }
    }

    override suspend fun list(input: ListPhoneNumbersInputEntity): ListPhoneNumbersOutput {
        logger.debug("listPhoneNumbers input: $input")

        // Omit the paging fields the caller left unset so the service applies its defaults.
        val queryInput =
            ListPhoneNumbersInputGraphQL(
                limit = Optional.presentIfNotNull(input.limit),
                nextToken = Optional.presentIfNotNull(input.nextToken),
            )

        try {
            val queryResponse = apiClient.listPhoneNumbersQuery(queryInput)

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretPhoneNumberError(queryResponse.errors.first())
            }

            val result = queryResponse.data?.listPhoneNumbers
            result?.let {
                return ListPhoneNumbersOutput(
                    items = it.items.map { item -> PhoneNumberTransformer.fromGraphQLToEntity(item.phoneNumber) },
                    nextToken = it.nextToken,
                )
            }
            logger.error("no phone numbers returned")
            throw PhoneNumberException.FailedException(
                StringConstants.NO_PHONE_NUMBERS_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw PhoneNumberException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretPhoneNumberException(e)
            }
        }
    }
}
