/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.PhoneMessageBatchResultTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.PhoneMessageTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.SortOrderTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesOutput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageBatchResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SealedPhoneMessageEntity
import com.sudoplatform.sudotelephony.graphql.type.DeletePhoneMessagesInput as DeletePhoneMessagesInputGraphQL
import com.sudoplatform.sudotelephony.graphql.type.ListPhoneMessagesInput as ListPhoneMessagesInputGraphQL
import com.sudoplatform.sudotelephony.graphql.type.UpdatePhoneMessagesInput as UpdatePhoneMessagesInputGraphQL
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.DeletePhoneMessagesInput as DeletePhoneMessagesInputEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesInput as ListPhoneMessagesInputEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.UpdatePhoneMessagesInput as UpdatePhoneMessagesInputEntity

/**
 * Implementation of [PhoneMessageService] backed by the GraphQL API.
 *
 * Returns messages exactly as the service sealed them; unsealing happens outside the data
 * layer so no key material passes through here.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLPhoneMessageService(
    private val apiClient: ApiClient,
    private val logger: Logger,
) : PhoneMessageService {
    override suspend fun get(id: String): SealedPhoneMessageEntity? {
        logger.debug("getPhoneMessage id: $id")

        try {
            val queryResponse = apiClient.getPhoneMessageQuery(id)

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretPhoneMessageError(queryResponse.errors.first())
            }

            // The service returns null when no message with the id belongs to the caller.
            val result = queryResponse.data?.getPhoneMessage ?: return null
            return PhoneMessageTransformer.fromGraphQLToSealedEntity(result.sealedPhoneMessage)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw PhoneMessageException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretPhoneMessageException(e)
            }
        }
    }

    override suspend fun list(input: ListPhoneMessagesInputEntity): ListPhoneMessagesOutput {
        logger.debug("listPhoneMessages phoneNumberId: ${input.phoneNumberId}")

        // Omit the optional fields the caller left unset so the service applies its defaults.
        val queryInput =
            ListPhoneMessagesInputGraphQL(
                phoneNumberId = input.phoneNumberId,
                conversationId = Optional.presentIfNotNull(input.conversationId),
                sortOrder =
                    Optional.presentIfNotNull(
                        input.sortOrder?.let { SortOrderTransformer.fromEntityToGraphQL(it) },
                    ),
                limit = Optional.presentIfNotNull(input.limit),
                nextToken = Optional.presentIfNotNull(input.nextToken),
            )

        try {
            val queryResponse = apiClient.listPhoneMessagesQuery(queryInput)

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretPhoneMessageError(queryResponse.errors.first())
            }

            val result = queryResponse.data?.listPhoneMessages
            result?.let {
                return ListPhoneMessagesOutput(
                    items =
                        it.items.map { item ->
                            PhoneMessageTransformer.fromGraphQLToSealedEntity(item.sealedPhoneMessage)
                        },
                    nextToken = it.nextToken,
                )
            }
            logger.error("no phone messages returned")
            throw PhoneMessageException.FailedException(
                StringConstants.NO_PHONE_MESSAGES_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw PhoneMessageException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretPhoneMessageException(e)
            }
        }
    }

    override suspend fun delete(input: DeletePhoneMessagesInputEntity): PhoneMessageBatchResultEntity {
        logger.debug("deletePhoneMessages count: ${input.ids.size}")

        try {
            val mutationResponse =
                apiClient.deletePhoneMessagesMutation(
                    DeletePhoneMessagesInputGraphQL(ids = input.ids),
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretPhoneMessageError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.deletePhoneMessages
            result?.let {
                return PhoneMessageBatchResultTransformer.fromGraphQLToEntity(
                    succeededIds = it.succeededIds,
                    failedIds = it.failedIds,
                )
            }
            logger.error("no batch result returned")
            throw PhoneMessageException.FailedException(
                StringConstants.NO_BATCH_RESULT_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw PhoneMessageException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretPhoneMessageException(e)
            }
        }
    }

    override suspend fun updateSeen(input: UpdatePhoneMessagesInputEntity): PhoneMessageBatchResultEntity {
        logger.debug("updatePhoneMessages count: ${input.ids.size} seen: ${input.seen}")

        try {
            val mutationResponse =
                apiClient.updatePhoneMessagesMutation(
                    UpdatePhoneMessagesInputGraphQL(ids = input.ids, seen = input.seen),
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretPhoneMessageError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.updatePhoneMessages
            result?.let {
                return PhoneMessageBatchResultTransformer.fromGraphQLToEntity(
                    succeededIds = it.succeededIds,
                    failedIds = it.failedIds,
                )
            }
            logger.error("no batch result returned")
            throw PhoneMessageException.FailedException(
                StringConstants.NO_BATCH_RESULT_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException ->
                    throw PhoneMessageException.AuthenticationException(cause = e)
                else -> throw ErrorTransformer.interpretPhoneMessageException(e)
            }
        }
    }
}
