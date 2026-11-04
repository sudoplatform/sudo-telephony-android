/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * The owner of a resource, taken from an ownership proof.
 *
 * @property id [String] ID of the owner, for example a Sudo ID.
 * @property issuer [String] Issuer of the ownership proof, for example
 *  `sudoplatform.sudoservice`.
 */
@Parcelize
data class Owner(
    val id: String,
    val issuer: String,
) : Parcelable
