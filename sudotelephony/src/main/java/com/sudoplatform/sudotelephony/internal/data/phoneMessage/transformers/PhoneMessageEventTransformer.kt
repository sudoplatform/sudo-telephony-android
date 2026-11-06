/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers

import com.sudoplatform.sudotelephony.graphql.OnPhoneMessageEventSubscription
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.OwnerTransformer
import com.sudoplatform.sudotelephony.internal.util.toDate
import com.sudoplatform.sudotelephony.types.PhoneMessageChangeType
import com.sudoplatform.sudotelephony.types.PhoneMessageEvent
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageChangeType as PhoneMessageChangeTypeGraphQL

/**
 * Transformer responsible for transforming the change reported by a message event between the
 * GraphQL enum and the public [PhoneMessageChangeType] type.
 */
internal object PhoneMessageChangeTypeTransformer {
    /** Transform the GraphQL [data] to its public type. */
    fun fromGraphQLToApi(data: PhoneMessageChangeTypeGraphQL): PhoneMessageChangeType =
        when (data) {
            PhoneMessageChangeTypeGraphQL.RECEIVED -> PhoneMessageChangeType.RECEIVED
            PhoneMessageChangeTypeGraphQL.UPDATED -> PhoneMessageChangeType.UPDATED
            PhoneMessageChangeTypeGraphQL.DELETED -> PhoneMessageChangeType.DELETED
            else -> PhoneMessageChangeType.UNKNOWN
        }
}

/**
 * Transformer responsible for transforming a message event from the GraphQL subscription
 * payload to the public type.
 *
 * A subscription payload is delivered straight to the subscriber without passing through a
 * use case, so it is mapped to the public type in one step rather than through an entity.
 */
internal object PhoneMessageEventTransformer {
    /**
     * Map the GraphQL subscription [data] to the public [PhoneMessageEvent] type.
     */
    fun fromGraphQLToApi(data: OnPhoneMessageEventSubscription.OnPhoneMessageEvent): PhoneMessageEvent =
        PhoneMessageEvent(
            id = data.id,
            phoneNumberId = data.phoneNumberId,
            conversationId = data.conversationId,
            type =
                PhoneMessageTypeTransformer.fromEntityToApi(
                    PhoneMessageTypeTransformer.fromGraphQLToEntity(data.type),
                ),
            direction =
                PhoneMessageDirectionTransformer.fromEntityToApi(
                    PhoneMessageDirectionTransformer.fromGraphQLToEntity(data.direction),
                ),
            changeType = PhoneMessageChangeTypeTransformer.fromGraphQLToApi(data.changeType),
            seen = data.seen,
            owner = data.owner,
            owners = data.owners.map { OwnerTransformer.fromGraphQLToApi(it) },
            version = data.version,
            updatedAt = data.updatedAtEpochMs.toDate(),
        )
}
