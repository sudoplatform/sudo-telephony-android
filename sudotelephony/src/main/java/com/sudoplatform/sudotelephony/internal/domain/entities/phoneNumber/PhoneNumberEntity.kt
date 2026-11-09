/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberTypeEntity
import java.util.Date

/**
 * Core entity representation of a provisioned phone number used in business logic.
 */
internal data class PhoneNumberEntity(
    val id: String,
    val owner: String,
    val owners: List<OwnerEntity>,
    val identityId: String,
    val keyRingId: String,
    val keyIds: List<String>,
    val phoneNumber: String,
    val countryCode: String,
    val numberType: PhoneNumberTypeEntity,
    val areaCode: String? = null,
    val state: PhoneNumberStateEntity,
    val failureReason: PhoneNumberFailureReasonEntity? = null,
    val gracePeriodEndsAt: Date? = null,
    val clientRefId: String? = null,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
)

/**
 * Core entity representation of the owner of a resource, taken from an ownership proof.
 */
internal data class OwnerEntity(
    val id: String,
    val issuer: String,
)

/**
 * Lifecycle state of a phone number.
 */
internal sealed class PhoneNumberStateEntity {
    data object Provisioning : PhoneNumberStateEntity()

    data object Active : PhoneNumberStateEntity()

    data object Suspended : PhoneNumberStateEntity()

    data object Released : PhoneNumberStateEntity()

    data object Failed : PhoneNumberStateEntity()

    /** The service reported a state this version of the SDK does not recognise. */
    data class Unknown(
        val value: String,
    ) : PhoneNumberStateEntity()
}

/**
 * The reason provisioning a phone number failed.
 */
internal sealed class PhoneNumberFailureReasonEntity {
    data object NumberUnavailable : PhoneNumberFailureReasonEntity()

    data object CarrierError : PhoneNumberFailureReasonEntity()

    data object OwnerDeleted : PhoneNumberFailureReasonEntity()

    /** The service reported a reason this version of the SDK does not recognise. */
    data class Unknown(
        val value: String,
    ) : PhoneNumberFailureReasonEntity()
}

/**
 * The encoding of a public key, which the service needs in order to read it.
 */
internal enum class PublicKeyFormatEntity {
    /** PKCS#1 RSA public key. */
    RSA_PUBLIC_KEY,

    /** X.509 SubjectPublicKeyInfo. */
    SPKI,
}
