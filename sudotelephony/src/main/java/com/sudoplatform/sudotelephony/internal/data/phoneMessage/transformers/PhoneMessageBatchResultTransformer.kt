/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageBatchResultEntity
import com.sudoplatform.sudotelephony.types.PhoneMessageBatchResult

/**
 * Transformer responsible for transforming a batch outcome between the entity type used in
 * business logic and the public type exposed to consumers.
 *
 * Apollo generates a separate result type per mutation, so each wire form is mapped to the
 * entity by its own overload.
 */
internal object PhoneMessageBatchResultTransformer {
    /** Map the succeeded and failed ids the service reported to an entity. */
    fun fromGraphQLToEntity(
        succeededIds: List<String>,
        failedIds: List<String>,
    ): PhoneMessageBatchResultEntity =
        PhoneMessageBatchResultEntity(
            succeededIds = succeededIds,
            failedIds = failedIds,
        )

    /** Map the [entity] to its public [PhoneMessageBatchResult] type. */
    fun fromEntityToApi(entity: PhoneMessageBatchResultEntity): PhoneMessageBatchResult =
        PhoneMessageBatchResult(
            succeededIds = entity.succeededIds,
            failedIds = entity.failedIds,
        )
}
