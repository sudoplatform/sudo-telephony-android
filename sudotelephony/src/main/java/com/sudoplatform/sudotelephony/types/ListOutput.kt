/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

/**
 * Wrapper around a paginated GraphQL list result. Exposes the [items] on this page and the
 * [nextToken] to pass back to request the next one.
 *
 * @property items [List] Items returned on this page.
 * @property nextToken [String] Token to pass to the next call to get the next page. `null`
 *  on the last page.
 */
data class ListOutput<T>(
    val items: List<T>,
    val nextToken: String? = null,
)
