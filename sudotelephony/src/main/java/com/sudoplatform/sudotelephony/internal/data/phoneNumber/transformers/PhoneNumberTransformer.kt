/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers

import com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.transformers.PhoneNumberTypeTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberEntity
import com.sudoplatform.sudotelephony.internal.util.toDate
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.graphql.fragment.PhoneNumber as PhoneNumberFragment

/**
 * Transformer responsible for transforming a provisioned phone number between the GraphQL
 * fragment, the entity type used in business logic, and the public type exposed to consumers.
 */
internal object PhoneNumberTransformer {
    /**
     * Transform the GraphQL [data] to its entity type.
     */
    fun fromGraphQLToEntity(data: PhoneNumberFragment): PhoneNumberEntity =
        PhoneNumberEntity(
            id = data.id,
            owner = data.owner,
            owners = data.owners.map { OwnerTransformer.fromGraphQLToEntity(it) },
            identityId = data.identityId,
            keyRingId = data.keyRingId,
            keyIds = data.keyIds,
            phoneNumber = data.phoneNumber,
            countryCode = data.countryCode,
            numberType = PhoneNumberTypeTransformer.fromGraphQLToEntity(data.numberType),
            areaCode = data.areaCode,
            state = PhoneNumberStateTransformer.fromGraphQLToEntity(data.state),
            failureReason = PhoneNumberFailureReasonTransformer.fromGraphQLToEntity(data.failureReason),
            gracePeriodEndsAt = data.gracePeriodEndsAtEpochMs?.toDate(),
            clientRefId = data.clientRefId,
            version = data.version,
            createdAt = data.createdAtEpochMs.toDate(),
            updatedAt = data.updatedAtEpochMs.toDate(),
        )

    /**
     * Transform the [entity] to its public [PhoneNumber] type.
     */
    fun fromEntityToApi(entity: PhoneNumberEntity): PhoneNumber =
        PhoneNumber(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.fromEntityToApi(it) },
            identityId = entity.identityId,
            keyRingId = entity.keyRingId,
            keyIds = entity.keyIds,
            phoneNumber = entity.phoneNumber,
            countryCode = entity.countryCode,
            numberType = PhoneNumberTypeTransformer.fromEntityToApi(entity.numberType),
            areaCode = entity.areaCode,
            state = PhoneNumberStateTransformer.fromEntityToApi(entity.state),
            failureReason = PhoneNumberFailureReasonTransformer.fromEntityToApi(entity.failureReason),
            gracePeriodEndsAt = entity.gracePeriodEndsAt,
            clientRefId = entity.clientRefId,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
