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
 * Lifecycle state of a phone number.
 */
sealed class PhoneNumberState : Parcelable {
    /** The order for the number is in progress with the telephony provider. */
    @Parcelize
    data object Provisioning : PhoneNumberState()

    /** The number is active and can send and receive messages. */
    @Parcelize
    data object Active : PhoneNumberState()

    /**
     * The user's entitlements no longer cover the number. It keeps receiving messages until
     * the grace period ends, then it is released.
     */
    @Parcelize
    data object Suspended : PhoneNumberState()

    /** The number has been returned to the telephony provider. */
    @Parcelize
    data object Released : PhoneNumberState()

    /** Provisioning failed. See [PhoneNumber.failureReason]. */
    @Parcelize
    data object Failed : PhoneNumberState()

    /**
     * The service reported a state this version of the SDK does not recognise.
     *
     * @property value [String] The state the service reported.
     */
    @Parcelize
    data class Unknown(
        val value: String,
    ) : PhoneNumberState()
}

/**
 * The reason provisioning a phone number failed.
 */
sealed class PhoneNumberFailureReason : Parcelable {
    /**
     * The number is no longer available, for example because someone else took it after the
     * search.
     */
    @Parcelize
    data object NumberUnavailable : PhoneNumberFailureReason()

    /** The telephony provider or carrier rejected the order. */
    @Parcelize
    data object CarrierError : PhoneNumberFailureReason()

    /** The Sudo or user that owned the number was deleted before its order was placed. */
    @Parcelize
    data object OwnerDeleted : PhoneNumberFailureReason()

    /**
     * The service reported a reason this version of the SDK does not recognise.
     *
     * @property value [String] The reason the service reported.
     */
    @Parcelize
    data class Unknown(
        val value: String,
    ) : PhoneNumberFailureReason()
}

/**
 * A phone number provisioned for a user.
 *
 * Provisioning returns the number in [PhoneNumberState.Provisioning]; it becomes
 * [PhoneNumberState.Active] or [PhoneNumberState.Failed] once the order completes with the
 * telephony provider.
 *
 * @property id [String] ID of the phone number.
 * @property owner [String] ID of the user who owns the phone number.
 * @property owners [List] Owners taken from the ownership proofs, which tie the phone number
 *  to a Sudo.
 * @property identityId [String] Identity ID of the user who owns the phone number.
 * @property keyRingId [String] ID of the key ring whose keys seal the number's messages.
 * @property keyIds [List] IDs of the keys in the key ring.
 * @property phoneNumber [String] The number in E.164 format, for example `+14155550123`.
 * @property countryCode [String] ISO 3166-1 alpha-2 country code of the number.
 * @property numberType [PhoneNumberType] The kind of number.
 * @property areaCode [String] Area code of the number. `null` where the country has no area
 *  codes.
 * @property state [PhoneNumberState] Lifecycle state of the number.
 * @property failureReason [PhoneNumberFailureReason] Why provisioning failed. Set only when
 *  [state] is [PhoneNumberState.Failed].
 * @property gracePeriodEndsAt [Date] When the number will be released. Set only when [state]
 *  is [PhoneNumberState.Suspended].
 * @property clientRefId [String] Client-supplied reference passed when provisioning, returned
 *  unchanged.
 * @property version [Int] Version of this object, incremented on each update.
 * @property createdAt [Date] When the object was created.
 * @property updatedAt [Date] When the object was last updated.
 */
@Parcelize
data class PhoneNumber(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val identityId: String,
    val keyRingId: String,
    val keyIds: List<String>,
    val phoneNumber: String,
    val countryCode: String,
    val numberType: PhoneNumberType,
    val areaCode: String? = null,
    val state: PhoneNumberState,
    val failureReason: PhoneNumberFailureReason? = null,
    val gracePeriodEndsAt: Date? = null,
    val clientRefId: String? = null,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
) : Parcelable {
    /** ID of the Sudo that owns the number, or `null` when the number belongs only to the user. */
    val sudoId: String?
        get() = owners.sudoId()
}
