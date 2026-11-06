/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types.inputs

/**
 * Input object containing the properties required to provision a phone number.
 *
 * The SDK generates the key pair that seals the number's messages, registers the public key
 * with the service, and keeps the private key on the device, so no key material is supplied
 * here.
 *
 * @property searchId [String] ID of one of the caller's searches that has not expired, as
 *  returned by
 *  [com.sudoplatform.sudotelephony.SudoTelephonyClient.searchAvailablePhoneNumbers].
 * @property phoneNumber [String] The number to provision, in E.164 format. Must be one of
 *  that search's results.
 * @property ownershipProofToken [String] Proof that the caller owns the Sudo the number will
 *  belong to, issued by the Sudo service for the audience
 *  [com.sudoplatform.sudotelephony.SudoTelephonyClient.OWNERSHIP_PROOF_AUDIENCE]. Required
 *  only when
 *  [com.sudoplatform.sudotelephony.types.ConfigurationData.sudoOwnershipProofRequired] is
 *  `true`; without it the number belongs to the user only.
 * @property clientRefId [String] Client-supplied reference, returned unchanged on the phone
 *  number. Repeating a request with the same value returns the number the first request
 *  provisioned rather than provisioning another.
 */
data class ProvisionPhoneNumberInput(
    val searchId: String,
    val phoneNumber: String,
    val ownershipProofToken: String? = null,
    val clientRefId: String? = null,
)
