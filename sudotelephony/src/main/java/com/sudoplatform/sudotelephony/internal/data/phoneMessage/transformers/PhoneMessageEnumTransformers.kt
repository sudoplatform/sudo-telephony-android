/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageDirectionEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageTypeEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SortOrderEntity
import com.sudoplatform.sudotelephony.types.PhoneMessageDirection
import com.sudoplatform.sudotelephony.types.PhoneMessageType
import com.sudoplatform.sudotelephony.types.SortOrder
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageDirection as PhoneMessageDirectionGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageType as PhoneMessageTypeGraphQL
import com.sudoplatform.sudotelephony.graphql.type.SortOrder as SortOrderGraphQL

/**
 * Transformer responsible for transforming between the [PhoneMessageTypeGraphQL] GraphQL
 * type, the [PhoneMessageTypeEntity] entity type, and the public [PhoneMessageType] type.
 */
internal object PhoneMessageTypeTransformer {
    /** Transform the GraphQL [data] to its entity type. */
    fun fromGraphQLToEntity(data: PhoneMessageTypeGraphQL): PhoneMessageTypeEntity =
        when (data) {
            PhoneMessageTypeGraphQL.SMS -> PhoneMessageTypeEntity.Sms
            is PhoneMessageTypeGraphQL.UNKNOWN__ -> PhoneMessageTypeEntity.Unknown(data.rawValue)
        }

    /** Transform the [entity] to its public [PhoneMessageType] type. */
    fun fromEntityToApi(entity: PhoneMessageTypeEntity): PhoneMessageType =
        when (entity) {
            PhoneMessageTypeEntity.Sms -> PhoneMessageType.Sms
            is PhoneMessageTypeEntity.Unknown -> PhoneMessageType.Unknown(entity.value)
        }
}

/**
 * Transformer responsible for transforming between the [PhoneMessageDirectionGraphQL] GraphQL
 * type, the [PhoneMessageDirectionEntity] entity type, and the public
 * [PhoneMessageDirection] type.
 */
internal object PhoneMessageDirectionTransformer {
    /** Transform the GraphQL [data] to its entity type. */
    fun fromGraphQLToEntity(data: PhoneMessageDirectionGraphQL): PhoneMessageDirectionEntity =
        when (data) {
            PhoneMessageDirectionGraphQL.INBOUND -> PhoneMessageDirectionEntity.Inbound
            is PhoneMessageDirectionGraphQL.UNKNOWN__ -> PhoneMessageDirectionEntity.Unknown(data.rawValue)
        }

    /** Transform the [entity] to its public [PhoneMessageDirection] type. */
    fun fromEntityToApi(entity: PhoneMessageDirectionEntity): PhoneMessageDirection =
        when (entity) {
            PhoneMessageDirectionEntity.Inbound -> PhoneMessageDirection.Inbound
            is PhoneMessageDirectionEntity.Unknown -> PhoneMessageDirection.Unknown(entity.value)
        }
}

/**
 * Transformer responsible for transforming a list order between the public [SortOrder] type,
 * the [SortOrderEntity] entity enum, and the [SortOrderGraphQL] GraphQL enum.
 */
internal object SortOrderTransformer {
    /** Transform the public [api] type to its entity enum. */
    fun fromApiToEntity(api: SortOrder): SortOrderEntity =
        when (api) {
            SortOrder.ASC -> SortOrderEntity.ASC
            SortOrder.DESC -> SortOrderEntity.DESC
        }

    /** Transform the [entity] to the GraphQL enum the service expects. */
    fun fromEntityToGraphQL(entity: SortOrderEntity): SortOrderGraphQL =
        when (entity) {
            SortOrderEntity.ASC -> SortOrderGraphQL.ASC
            SortOrderEntity.DESC -> SortOrderGraphQL.DESC
        }
}
