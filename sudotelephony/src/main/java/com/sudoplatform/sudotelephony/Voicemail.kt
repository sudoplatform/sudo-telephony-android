package com.sudoplatform.sudotelephony

import android.os.Parcelable
import com.sudoplatform.sudotelephony.fragment.SealedVoicemail
import kotlinx.android.parcel.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.time.Instant
import java.util.*

/**
 * A voicemail record.
 */
@Parcelize
data class Voicemail(
    /**
     * Unique ID of the voicemail record
     */
    val id: String,
    /**
     * The ID of the Sudo Platform user that owns the voicemail.
     */
    val owner: String,
    /**
     * The ID of the Sudo that received the voicemail.
     */
    val sudoOwner: String,
    /**
     * The ID of the `PhoneNumber` associated with the voicemail.
     */
    var phoneNumberId: String,
    /**
     * The ID of the `CallRecord` associated with the voicemail.
     */
    var callRecordId: String?,
    /**
     * Timestamp that represents the time the voicemail was created.
     */
    val created: Instant,
    /**
     * Timestamp that represents the last time the voicemail was updated.
     */
    val updated: Instant,
    /**
     * The E.164 formatted phone number that received the voicemail.
     */
    val localPhoneNumber: String,
    /**
     * The E.164 formatted phone number of the caller.
     */
    val remotePhoneNumber: String,
    /**
     * The duration of the voicemail recording in seconds.
     */
    val durationSeconds: Int,
    /**
     * The media object that can be used to download the voicemail recording.
     */
    val media: MediaObject
) : Parcelable {

    companion object {
        fun createFrom(sealedVoicemail: SealedVoicemail, keyManager: TelephonyKeyManager): Voicemail {
            val sealedData =
                sealedVoicemail.sealed().firstOrNull { it.keyId() == keyManager.getKeyId() }
                    ?: throw TelephonyDecryptSealedDataException()

            val decoder = Base64.getDecoder()
            val sealedLocalData = decoder.decode(sealedData.localPhoneNumber())
            val sealedRemoteData = decoder.decode(sealedData.remotePhoneNumber())
            val sealedDuration = decoder.decode(sealedData.durationSeconds())

            // decrypt the sealed data
            val decryptedLocal = keyManager.decryptSealedData(sealedLocalData)
            val decryptedRemote = keyManager.decryptSealedData(sealedRemoteData)
            val decryptedDuration = keyManager.decryptSealedData(sealedDuration)

            val localPhoneNumber = String(decryptedLocal, Charset.defaultCharset())
            val remotePhoneNumber = String(decryptedRemote, Charset.defaultCharset())
            val durationSeconds = ByteBuffer.wrap(decryptedDuration).order(ByteOrder.BIG_ENDIAN).int

            return Voicemail(
                sealedVoicemail.id(),
                sealedVoicemail.owner(),
                sealedVoicemail.sudoOwner(),
                sealedVoicemail.phoneNumberId(),
                sealedVoicemail.callRecordId(),
                Instant.ofEpochMilli(sealedVoicemail.createdAtEpochMs().toLong()),
                Instant.ofEpochMilli(sealedVoicemail.updatedAtEpochMs().toLong()),
                localPhoneNumber,
                remotePhoneNumber,
                durationSeconds,
                MediaObject.fromS3Media(sealedData.media().fragments().s3MediaObject())
            )
        }
    }
}
