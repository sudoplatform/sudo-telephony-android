/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberStateEntity
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberState as PhoneNumberStateGraphQL

/**
 * Transformer responsible for transforming between the [PhoneNumberStateGraphQL] GraphQL
 * type, the [PhoneNumberStateEntity] entity type, and the public [PhoneNumberState] type.
 */
internal object PhoneNumberStateTransformer {
    /**
     * Transform the GraphQL [data] to its entity type.
     */
    fun fromGraphQLToEntity(data: PhoneNumberStateGraphQL): PhoneNumberStateEntity =
        when (data) {
            PhoneNumberStateGraphQL.PROVISIONING -> PhoneNumberStateEntity.Provisioning
            PhoneNumberStateGraphQL.ACTIVE -> PhoneNumberStateEntity.Active
            PhoneNumberStateGraphQL.SUSPENDED -> PhoneNumberStateEntity.Suspended
            PhoneNumberStateGraphQL.RELEASED -> PhoneNumberStateEntity.Released
            PhoneNumberStateGraphQL.FAILED -> PhoneNumberStateEntity.Failed
            is PhoneNumberStateGraphQL.UNKNOWN__ -> PhoneNumberStateEntity.Unknown(data.rawValue)
        }

    /**
     * Transform the [entity] to its public [PhoneNumberState] type.
     */
    fun fromEntityToApi(entity: PhoneNumberStateEntity): PhoneNumberState =
        when (entity) {
            PhoneNumberStateEntity.Provisioning -> PhoneNumberState.Provisioning
            PhoneNumberStateEntity.Active -> PhoneNumberState.Active
            PhoneNumberStateEntity.Suspended -> PhoneNumberState.Suspended
            PhoneNumberStateEntity.Released -> PhoneNumberState.Released
            PhoneNumberStateEntity.Failed -> PhoneNumberState.Failed
            is PhoneNumberStateEntity.Unknown -> PhoneNumberState.Unknown(entity.value)
        }
}
