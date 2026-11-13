/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

/**
 * The outcome for one key of [com.sudoplatform.sudotelephony.SudoTelephonyClient.createSingletonKeysIfAbsent].
 *
 * @property created [Boolean] Whether the key had to be created. `true` means the device's key
 *  material changed, so a key backup made before the call does not contain it.
 * @property keyId [String] The key's identifier.
 */
data class KeyResult(
    val created: Boolean,
    val keyId: String,
)

/**
 * The result of [com.sudoplatform.sudotelephony.SudoTelephonyClient.createSingletonKeysIfAbsent].
 *
 * @property keyPair [KeyResult] The singleton key pair every phone number is sealed to.
 */
data class CreateSingletonKeysIfAbsentResult(
    val keyPair: KeyResult,
)
