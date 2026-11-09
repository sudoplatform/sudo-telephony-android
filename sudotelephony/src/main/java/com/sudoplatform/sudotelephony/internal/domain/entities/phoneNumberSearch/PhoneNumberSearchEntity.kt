/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch

import java.util.Date

/**
 * Core entity representation of a search for available phone numbers used in business logic.
 */
internal data class PhoneNumberSearchEntity(
    val id: String,
    val countryCode: String,
    val areaCode: String? = null,
    val results: List<AvailablePhoneNumberEntity>,
    val expiresAt: Date,
    val owner: String,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
)

/**
 * Core entity representation of a phone number the service is offering to provision.
 */
internal data class AvailablePhoneNumberEntity(
    val phoneNumber: String,
    val countryCode: String,
    val numberType: PhoneNumberTypeEntity,
    val locality: String? = null,
    val region: String? = null,
)

/**
 * The kind of phone number.
 */
internal sealed class PhoneNumberTypeEntity {
    data object Local : PhoneNumberTypeEntity()

    /** The service reported a kind this version of the SDK does not recognise. */
    data class Unknown(
        val value: String,
    ) : PhoneNumberTypeEntity()
}
