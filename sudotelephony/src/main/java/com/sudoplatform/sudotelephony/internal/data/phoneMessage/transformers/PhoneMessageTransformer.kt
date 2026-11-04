/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers

import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.OwnerTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SealedPhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.util.toDate
import com.sudoplatform.sudotelephony.types.PhoneMessage
import com.sudoplatform.sudotelephony.graphql.fragment.SealedPhoneMessage as SealedPhoneMessageFragment

/**
 * Transformer responsible for transforming a phone message between the GraphQL fragment, the
 * entity types used in business logic, and the public type exposed to consumers.
 *
 * The wire and sealed entity forms carry the sealed field values verbatim; only
 * [fromSealedToUnsealedEntity] produces readable text, and it takes the already-unsealed
 * values so that no key material reaches this transformer.
 */
internal object PhoneMessageTransformer {
    /**
     * Map the GraphQL [data] to a sealed entity, leaving the sealed fields sealed.
     */
    fun fromGraphQLToSealedEntity(data: SealedPhoneMessageFragment): SealedPhoneMessageEntity =
        SealedPhoneMessageEntity(
            id = data.id,
            owner = data.owner,
            owners = data.owners.map { OwnerTransformer.fromGraphQLToEntity(it) },
            phoneNumberId = data.phoneNumberId,
            conversationId = data.conversationId,
            type = PhoneMessageTypeTransformer.fromGraphQLToEntity(data.type),
            direction = PhoneMessageDirectionTransformer.fromGraphQLToEntity(data.direction),
            localPhoneNumber = data.localPhoneNumber,
            remotePhoneNumber = data.remotePhoneNumber,
            body = data.body,
            segmentCount = data.segmentCount,
            mediaOmitted = data.mediaOmitted,
            seen = data.seen,
            receivedAt = data.receivedAtEpochMs.toDate(),
            version = data.version,
            createdAt = data.createdAtEpochMs.toDate(),
            updatedAt = data.updatedAtEpochMs.toDate(),
        )

    /**
     * Combine a [sealed] message with its unsealed field values.
     *
     * @param sealed [SealedPhoneMessageEntity] The message as the service returned it.
     * @param remotePhoneNumber [String] The unsealed other-party number, or `null` when the
     *  message carried none.
     * @param body [String] The unsealed message text.
     * @return The readable [PhoneMessageEntity].
     */
    fun fromSealedToUnsealedEntity(
        sealed: SealedPhoneMessageEntity,
        remotePhoneNumber: String?,
        body: String,
    ): PhoneMessageEntity =
        PhoneMessageEntity(
            id = sealed.id,
            owner = sealed.owner,
            owners = sealed.owners,
            phoneNumberId = sealed.phoneNumberId,
            conversationId = sealed.conversationId,
            type = sealed.type,
            direction = sealed.direction,
            localPhoneNumber = sealed.localPhoneNumber,
            remotePhoneNumber = remotePhoneNumber,
            body = body,
            segmentCount = sealed.segmentCount,
            mediaOmitted = sealed.mediaOmitted,
            seen = sealed.seen,
            receivedAt = sealed.receivedAt,
            version = sealed.version,
            createdAt = sealed.createdAt,
            updatedAt = sealed.updatedAt,
        )

    /**
     * Map an [entity] to the public [PhoneMessage] type.
     */
    fun fromEntityToApi(entity: PhoneMessageEntity): PhoneMessage =
        PhoneMessage(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.fromEntityToApi(it) },
            phoneNumberId = entity.phoneNumberId,
            conversationId = entity.conversationId,
            type = PhoneMessageTypeTransformer.fromEntityToApi(entity.type),
            direction = PhoneMessageDirectionTransformer.fromEntityToApi(entity.direction),
            localPhoneNumber = entity.localPhoneNumber,
            remotePhoneNumber = entity.remotePhoneNumber,
            body = entity.body,
            segmentCount = entity.segmentCount,
            mediaOmitted = entity.mediaOmitted,
            seen = entity.seen,
            receivedAt = entity.receivedAt,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
