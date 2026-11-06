/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.data

import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PartialPhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageDirectionEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageTypeEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SealedPhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.OwnerEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberFailureReasonEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberStateEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.AvailablePhoneNumberEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberTypeEntity
import com.sudoplatform.sudotelephony.keys.KeyPair
import com.sudoplatform.sudotelephony.types.Owner
import com.sudoplatform.sudotelephony.types.PhoneMessageChangeType
import com.sudoplatform.sudotelephony.types.PhoneMessageDirection
import com.sudoplatform.sudotelephony.types.PhoneMessageEvent
import com.sudoplatform.sudotelephony.types.PhoneMessageType
import java.util.Date

/**
 * Builders for consistent, valid domain fixtures used across unit tests.
 */
internal object EntityDataFactory {
    /** The canonical set of ISO 3166-1 alpha-2 country codes used in fixtures. */
    val countryCodes = listOf("US", "CA", "GB", "AU")

    /** Fixed epoch millisecond values so date mapping is deterministic. */
    const val CREATED_AT_EPOCH_MS = 1_700_000_000_000.0
    const val UPDATED_AT_EPOCH_MS = 1_700_000_001_000.0
    const val EXPIRES_AT_EPOCH_MS = 1_700_000_900_000.0

    /** An [AvailablePhoneNumberEntity] fixture. */
    fun availablePhoneNumberEntity(
        phoneNumber: String = "+14155550123",
        countryCode: String = "US",
        numberType: PhoneNumberTypeEntity = PhoneNumberTypeEntity.LOCAL,
        locality: String? = "San Francisco",
        region: String? = "CA",
    ) = AvailablePhoneNumberEntity(
        phoneNumber = phoneNumber,
        countryCode = countryCode,
        numberType = numberType,
        locality = locality,
        region = region,
    )

    /** A [PhoneNumberSearchEntity] fixture. */
    fun phoneNumberSearchEntity(
        id: String = "mockSearchId",
        countryCode: String = "US",
        areaCode: String? = "415",
        results: List<AvailablePhoneNumberEntity> = listOf(availablePhoneNumberEntity()),
        expiresAt: Date = Date(EXPIRES_AT_EPOCH_MS.toLong()),
        owner: String = "mockOwner",
        version: Int = 1,
        createdAt: Date = Date(CREATED_AT_EPOCH_MS.toLong()),
        updatedAt: Date = Date(UPDATED_AT_EPOCH_MS.toLong()),
    ) = PhoneNumberSearchEntity(
        id = id,
        countryCode = countryCode,
        areaCode = areaCode,
        results = results,
        expiresAt = expiresAt,
        owner = owner,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /** An [OwnerEntity] fixture. */
    fun ownerEntity(
        id: String = "mockSudoId",
        issuer: String = "sudoplatform.sudoservice",
    ) = OwnerEntity(id = id, issuer = issuer)

    /** A [PhoneNumberEntity] fixture. */
    fun phoneNumberEntity(
        id: String = "mockPhoneNumberId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> = listOf(ownerEntity()),
        identityId: String = "mockIdentityId",
        keyRingId: String = "sudo-telephony.mockOwner",
        keyIds: List<String> = listOf("mockKeyId"),
        phoneNumber: String = "+14155550123",
        countryCode: String = "US",
        numberType: PhoneNumberTypeEntity = PhoneNumberTypeEntity.LOCAL,
        areaCode: String? = "415",
        state: PhoneNumberStateEntity = PhoneNumberStateEntity.PROVISIONING,
        failureReason: PhoneNumberFailureReasonEntity? = null,
        gracePeriodEndsAt: Date? = null,
        clientRefId: String? = null,
        version: Int = 1,
        createdAt: Date = Date(CREATED_AT_EPOCH_MS.toLong()),
        updatedAt: Date = Date(UPDATED_AT_EPOCH_MS.toLong()),
    ) = PhoneNumberEntity(
        id = id,
        owner = owner,
        owners = owners,
        identityId = identityId,
        keyRingId = keyRingId,
        keyIds = keyIds,
        phoneNumber = phoneNumber,
        countryCode = countryCode,
        numberType = numberType,
        areaCode = areaCode,
        state = state,
        failureReason = failureReason,
        gracePeriodEndsAt = gracePeriodEndsAt,
        clientRefId = clientRefId,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /**
     * A real RSA key pair, so the public key encoding the service is told about is the one a
     * detector actually derives from the bytes rather than a hard-coded guess. Generated once
     * because 2048-bit generation is slow.
     */
    private val rsaKeyPair: java.security.KeyPair by lazy {
        java.security.KeyPairGenerator
            .getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
    }

    /** A [KeyPair] fixture backed by a real RSA key pair. */
    fun keyPair(
        keyId: String = "mockKeyId",
        keyRingId: String = "sudo-telephony.mockOwner",
    ) = KeyPair(
        keyId = keyId,
        keyRingId = keyRingId,
        publicKey = rsaKeyPair.public.encoded,
        privateKey = rsaKeyPair.private.encoded,
    )

    /** The sealed values used in message fixtures, as the service would return them. */
    const val SEALED_BODY = "sealedBodyBase64"
    const val SEALED_REMOTE_NUMBER = "sealedRemoteNumberBase64"

    /** A [SealedPhoneMessageEntity] fixture, with its sealed fields still sealed. */
    fun sealedPhoneMessageEntity(
        id: String = "mockMessageId",
        phoneNumberId: String = "mockPhoneNumberId",
        conversationId: String = "mockConversationId",
        remotePhoneNumber: String? = SEALED_REMOTE_NUMBER,
        body: String = SEALED_BODY,
        seen: Boolean = false,
    ) = SealedPhoneMessageEntity(
        id = id,
        owner = "mockOwner",
        owners = listOf(ownerEntity()),
        phoneNumberId = phoneNumberId,
        conversationId = conversationId,
        type = PhoneMessageTypeEntity.SMS,
        direction = PhoneMessageDirectionEntity.INBOUND,
        localPhoneNumber = "+14155550123",
        remotePhoneNumber = remotePhoneNumber,
        body = body,
        segmentCount = 1,
        mediaOmitted = false,
        seen = seen,
        receivedAt = Date(CREATED_AT_EPOCH_MS.toLong()),
        version = 1,
        createdAt = Date(CREATED_AT_EPOCH_MS.toLong()),
        updatedAt = Date(UPDATED_AT_EPOCH_MS.toLong()),
    )

    /** A [PhoneMessageEntity] fixture with readable fields. */
    fun phoneMessageEntity(
        id: String = "mockMessageId",
        phoneNumberId: String = "mockPhoneNumberId",
        remotePhoneNumber: String? = "+14155559876",
        body: String = "Hello there",
    ) = PhoneMessageEntity(
        id = id,
        owner = "mockOwner",
        owners = listOf(ownerEntity()),
        phoneNumberId = phoneNumberId,
        conversationId = "mockConversationId",
        type = PhoneMessageTypeEntity.SMS,
        direction = PhoneMessageDirectionEntity.INBOUND,
        localPhoneNumber = "+14155550123",
        remotePhoneNumber = remotePhoneNumber,
        body = body,
        segmentCount = 1,
        mediaOmitted = false,
        seen = false,
        receivedAt = Date(CREATED_AT_EPOCH_MS.toLong()),
        version = 1,
        createdAt = Date(CREATED_AT_EPOCH_MS.toLong()),
        updatedAt = Date(UPDATED_AT_EPOCH_MS.toLong()),
    )

    /** A [PartialPhoneMessageEntity] fixture matching [sealedPhoneMessageEntity]. */
    fun partialPhoneMessageEntity(id: String = "mockMessageId") =
        PartialPhoneMessageEntity(
            id = id,
            owner = "mockOwner",
            owners = listOf(ownerEntity()),
            phoneNumberId = "mockPhoneNumberId",
            conversationId = "mockConversationId",
            type = PhoneMessageTypeEntity.SMS,
            direction = PhoneMessageDirectionEntity.INBOUND,
            localPhoneNumber = "+14155550123",
            segmentCount = 1,
            mediaOmitted = false,
            seen = false,
            receivedAt = Date(CREATED_AT_EPOCH_MS.toLong()),
            version = 1,
            createdAt = Date(CREATED_AT_EPOCH_MS.toLong()),
            updatedAt = Date(UPDATED_AT_EPOCH_MS.toLong()),
        )

    /** A public [PhoneMessageEvent] fixture, as a subscription would deliver it. */
    fun phoneMessageEvent(
        id: String = "mockMessageId",
        changeType: PhoneMessageChangeType = PhoneMessageChangeType.RECEIVED,
        seen: Boolean = false,
        owners: List<Owner> = listOf(Owner(id = "mockSudoId", issuer = "sudoplatform.sudoservice")),
    ) = PhoneMessageEvent(
        id = id,
        phoneNumberId = "mockPhoneNumberId",
        conversationId = "mockConversationId",
        type = PhoneMessageType.SMS,
        direction = PhoneMessageDirection.INBOUND,
        changeType = changeType,
        seen = seen,
        owner = "mockOwner",
        owners = owners,
        version = 1,
        updatedAt = Date(UPDATED_AT_EPOCH_MS.toLong()),
    )

    /** A [ConfigurationDataEntity] fixture. */
    fun configurationDataEntity(sudoOwnershipProofRequired: Boolean = true) =
        ConfigurationDataEntity(
            sudoOwnershipProofRequired = sudoOwnershipProofRequired,
            phoneNumberSearchDefaultLimit = 10,
            phoneNumberSearchMaxLimit = 50,
            phoneNumberSearchLifetimeMs = 900_000,
            listDefaultLimit = 20,
            listMaxLimit = 100,
            deletePhoneMessagesLimit = 100,
            updatePhoneMessagesLimit = 100,
        )
}
