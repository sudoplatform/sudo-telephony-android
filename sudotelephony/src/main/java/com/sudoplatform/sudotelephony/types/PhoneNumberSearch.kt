/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * The result of a search for available phone numbers.
 *
 * The service keeps the search so that provisioning can check the requested number came from
 * it. Pass [id] as the search id when provisioning one of the [results].
 *
 * @property id [String] ID of the search.
 * @property countryCode [String] ISO 3166-1 alpha-2 country code that was searched.
 * @property areaCode [String] Area code that was searched. `null` for a country-wide search.
 * @property results [List] The available numbers, without duplicates and at most the
 *  requested limit.
 * @property expiresAt [Date] When provisioning from this search is refused. The carrier does
 *  not reserve the numbers, so one can be taken by someone else sooner.
 * @property owner [String] ID of the user who made the search.
 * @property version [Int] Version of this object, incremented on each update.
 * @property createdAt [Date] When the object was created.
 * @property updatedAt [Date] When the object was last updated.
 */
@Parcelize
data class PhoneNumberSearch(
    val id: String,
    val countryCode: String,
    val areaCode: String? = null,
    val results: List<AvailablePhoneNumber>,
    val expiresAt: Date,
    val owner: String,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
) : Parcelable
