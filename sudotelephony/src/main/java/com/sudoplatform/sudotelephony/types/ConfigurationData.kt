/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Duration

/**
 * How the telephony service deployment is configured, so that a client can follow its rules
 * without hard-coding them.
 *
 * @property sudoOwnershipProofRequired [Boolean] Whether provisioning a phone number requires
 *  a Sudo ownership proof. When `false`, a number provisioned without a proof belongs to the
 *  user only.
 * @property phoneNumberSearchDefaultLimit [Int] The number of results a phone number search
 *  returns when no limit is set.
 * @property phoneNumberSearchMaxLimit [Int] The largest limit a phone number search accepts.
 * @property phoneNumberSearchLifetime [Duration] How long a phone number search can be used to
 *  provision a number after it is created.
 * @property listDefaultLimit [Int] The page size of the list methods when no limit is set.
 * @property listMaxLimit [Int] The largest page the list methods return.
 * @property deletePhoneMessagesLimit [Int] The most messages one call to delete phone messages
 *  may name.
 * @property updatePhoneMessagesLimit [Int] The most messages one call to update phone messages
 *  may name.
 */
@Parcelize
data class ConfigurationData(
    val sudoOwnershipProofRequired: Boolean,
    val phoneNumberSearchDefaultLimit: Int,
    val phoneNumberSearchMaxLimit: Int,
    val phoneNumberSearchLifetime: Duration,
    val listDefaultLimit: Int,
    val listMaxLimit: Int,
    val deletePhoneMessagesLimit: Int,
    val updatePhoneMessagesLimit: Int,
) : Parcelable
