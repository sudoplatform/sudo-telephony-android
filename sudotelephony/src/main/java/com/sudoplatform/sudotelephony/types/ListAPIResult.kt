/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

/**
 * Result of an API that returns a page of items, some of which may only be partly readable.
 *
 * @param T The type of a fully readable item.
 * @param P The type of an item that could only be partly read.
 */
sealed class ListAPIResult<out T, out P> {
    /**
     * Every item on the page was read.
     *
     * @property result [ListSuccessResult] The items and the token for the next page.
     */
    data class Success<T>(
        val result: ListSuccessResult<T>,
    ) : ListAPIResult<T, Nothing>()

    /**
     * Some items on the page could not be fully read. Those are returned separately, each with
     * the exception that explains why.
     *
     * @property result [ListPartialResult] The readable items, the partly read items and the
     *  token for the next page.
     */
    data class Partial<T, P>(
        val result: ListPartialResult<T, P>,
    ) : ListAPIResult<T, P>()

    /**
     * A page where every item was read.
     *
     * @property items [List] The items on the page.
     * @property nextToken [String] The token for the next page, or `null` on the last page.
     */
    data class ListSuccessResult<T>(
        val items: List<T>,
        val nextToken: String?,
    )

    /**
     * A page where some items could not be fully read.
     *
     * @property items [List] The items that were read.
     * @property failed [List] The items that could only be partly read, each with its cause.
     * @property nextToken [String] The token for the next page, or `null` on the last page.
     */
    data class ListPartialResult<T, P>(
        val items: List<T>,
        val failed: List<PartialResult<P>>,
        val nextToken: String?,
    )
}

/**
 * An item that could only be partly read.
 *
 * @property partial [P] The fields of the item that could be read.
 * @property cause [Exception] Why the rest of the item could not be read.
 */
data class PartialResult<P>(
    val partial: P,
    val cause: Exception,
)
