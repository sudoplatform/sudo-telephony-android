/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

import android.os.Parcelable
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import kotlinx.parcelize.Parcelize

/**
 * The owner of a resource, taken from an ownership proof.
 *
 * @property id [String] ID of the owner, for example a Sudo ID.
 * @property issuer [String] Issuer of the ownership proof, for example
 *  [SudoTelephonyClient.SUDO_OWNER_ISSUER].
 */
@Parcelize
data class Owner(
    val id: String,
    val issuer: String,
) : Parcelable

/** ID of the Sudo among these owners, or `null` when none of them is a Sudo. */
fun List<Owner>.sudoId(): String? = firstOrNull { it.issuer == SudoTelephonyClient.SUDO_OWNER_ISSUER }?.id
