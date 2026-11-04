/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * The kind of a phone number.
 */
enum class PhoneNumberType {
    /** A geographic number tied to an area code. */
    LOCAL,

    /** The service reported a kind this version of the SDK does not recognise. */
    UNKNOWN,
}

/**
 * A phone number the telephony service is offering to provision.
 *
 * The carrier does not reserve an offered number, so it can be taken by someone else before
 * it is provisioned.
 *
 * @property phoneNumber [String] The number in E.164 format, for example `+14155550123`.
 * @property countryCode [String] ISO 3166-1 alpha-2 country code of the number, for example
 *  `US`.
 * @property numberType [PhoneNumberType] The kind of number.
 * @property locality [String] City or rate centre of the number, if the carrier reports one.
 * @property region [String] State or province code of the number, for example `NY` or `ON`.
 */
@Parcelize
data class AvailablePhoneNumber(
    val phoneNumber: String,
    val countryCode: String,
    val numberType: PhoneNumberType,
    val locality: String? = null,
    val region: String? = null,
) : Parcelable
