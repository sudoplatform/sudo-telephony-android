/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types.inputs

/**
 * Input object containing the properties used to list the caller's phone numbers.
 *
 * @property limit [Int] Maximum number of items per page. When `null` the service returns up
 *  to 20. A limit above 100 returns at most 100 items; a limit below 1 is rejected.
 * @property nextToken [String] The token returned by the previous page. Omit for the first
 *  page.
 */
data class ListPhoneNumbersInput(
    val limit: Int? = null,
    val nextToken: String? = null,
)
