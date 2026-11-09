/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberFailureReasonEntity
import com.sudoplatform.sudotelephony.types.PhoneNumberFailureReason
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberFailureReason as PhoneNumberFailureReasonGraphQL

/**
 * Transformer responsible for transforming between the [PhoneNumberFailureReasonGraphQL]
 * GraphQL type, the [PhoneNumberFailureReasonEntity] entity type, and the public
 * [PhoneNumberFailureReason] type.
 */
internal object PhoneNumberFailureReasonTransformer {
    /**
     * Transform the GraphQL [data] to its entity type, or `null` when the number has not
     * failed.
     */
    fun fromGraphQLToEntity(data: PhoneNumberFailureReasonGraphQL?): PhoneNumberFailureReasonEntity? =
        when (data) {
            null -> null
            PhoneNumberFailureReasonGraphQL.NUMBER_UNAVAILABLE -> PhoneNumberFailureReasonEntity.NumberUnavailable
            PhoneNumberFailureReasonGraphQL.CARRIER_ERROR -> PhoneNumberFailureReasonEntity.CarrierError
            PhoneNumberFailureReasonGraphQL.OWNER_DELETED -> PhoneNumberFailureReasonEntity.OwnerDeleted
            is PhoneNumberFailureReasonGraphQL.UNKNOWN__ -> PhoneNumberFailureReasonEntity.Unknown(data.rawValue)
        }

    /**
     * Transform the [entity] to its public [PhoneNumberFailureReason] type, or `null` when
     * the number has not failed.
     */
    fun fromEntityToApi(entity: PhoneNumberFailureReasonEntity?): PhoneNumberFailureReason? =
        when (entity) {
            null -> null
            PhoneNumberFailureReasonEntity.NumberUnavailable -> PhoneNumberFailureReason.NumberUnavailable
            PhoneNumberFailureReasonEntity.CarrierError -> PhoneNumberFailureReason.CarrierError
            PhoneNumberFailureReasonEntity.OwnerDeleted -> PhoneNumberFailureReason.OwnerDeleted
            is PhoneNumberFailureReasonEntity.Unknown -> PhoneNumberFailureReason.Unknown(entity.value)
        }
}
