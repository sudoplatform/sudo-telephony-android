/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.configuration

/**
 * Core entity representation of the telephony service configuration.
 *
 * @property sudoOwnershipProofRequired [Boolean] Whether provisioning requires a Sudo
 *  ownership proof.
 * @property phoneNumberSearchDefaultLimit [Int] Results a search returns when no limit is set.
 * @property phoneNumberSearchMaxLimit [Int] The largest limit a search accepts.
 * @property phoneNumberSearchLifetimeMs [Int] How long, in milliseconds, a search can be used
 *  to provision.
 * @property listDefaultLimit [Int] Page size of the list queries when no limit is set.
 * @property listMaxLimit [Int] The largest page the list queries return.
 * @property deletePhoneMessagesLimit [Int] The most messages one delete may name.
 * @property updatePhoneMessagesLimit [Int] The most messages one update may name.
 */
internal data class ConfigurationDataEntity(
    val sudoOwnershipProofRequired: Boolean,
    val phoneNumberSearchDefaultLimit: Int,
    val phoneNumberSearchMaxLimit: Int,
    val phoneNumberSearchLifetimeMs: Int,
    val listDefaultLimit: Int,
    val listMaxLimit: Int,
    val deletePhoneMessagesLimit: Int,
    val updatePhoneMessagesLimit: Int,
)
