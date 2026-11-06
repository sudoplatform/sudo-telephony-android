/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.common

/**
 * Core entity representation of a page of items, some of which may only be partly readable.
 */
internal sealed class ListAPIResultEntity<out T, out P> {
    /** Every item on the page was read. */
    data class Success<T>(
        val result: ListSuccessResultEntity<T>,
    ) : ListAPIResultEntity<T, Nothing>()

    /** Some items on the page could not be fully read. */
    data class Partial<T, P>(
        val result: ListPartialResultEntity<T, P>,
    ) : ListAPIResultEntity<T, P>()
}

/** A page where every item was read. */
internal data class ListSuccessResultEntity<T>(
    val items: List<T>,
    val nextToken: String?,
)

/** A page where some items could not be fully read. */
internal data class ListPartialResultEntity<T, P>(
    val items: List<T>,
    val failed: List<PartialResultEntity<P>>,
    val nextToken: String?,
)

/** An item that could only be partly read, with why. */
internal data class PartialResultEntity<P>(
    val partial: P,
    val cause: Exception,
)
