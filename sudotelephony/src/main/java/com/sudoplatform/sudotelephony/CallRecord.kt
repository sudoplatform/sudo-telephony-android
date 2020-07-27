package com.sudoplatform.sudotelephony

import android.os.Parcelable
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudotelephony.fragment.SealedCallRecord
import com.sudoplatform.sudotelephony.type.CallState
import com.sudoplatform.sudotelephony.type.Direction
import kotlinx.android.parcel.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.time.Instant
import java.util.*

/**
 * Voicemail data belonging to a call record
 */
@Parcelize
data class VoicemailData (
     /**
      * Unique ID of the voicemail record
      */
     val id: String,
     /**
      * The duration of the voicemail recording in seconds
      */
     val durationSeconds: Int,
     /**
      * The media object that can be used to download the voicemail recording
      */
     val media: MediaObject
) : Parcelable


@Parcelize
data class CallRecord(
     /**
      * Unique ID of the call record
      */
     val id: String,
     /**
      * The user ID of the owner of the call record (also referred to as the subject)
      */
     val owner: String,
     /**
      * The ID of the sudo that made or received the call
      */
     val sudoOwner: String,
     /**
      * The ID of the `PhoneNumber` associated with the call record
      */
     val phoneNumberId: String,
     /**
      * The direction of the call. Either `outbound` or `inbound`
      */
     val direction: Direction,
     /**
      * The state of the call record
      */
     val state: CallRecordState,
     /**
      * Timestamp that represents the last time the call was updated
      */
     val updated: Instant,
     /**
      * Timestamp that represents the time the call was created
      */
     val created: Instant,
     /**
      * The E164Number of the local phone number
      */
     val localPhoneNumber: String,
     /**
      * The E164Number of the remote phone number
      */
     val remotePhoneNumber: String,
     /**
      * The duration of the call in seconds
      */
     val durationSeconds: Int,
     /**
      * The voicemail data of this call record if it exists
      */
     val voicemail: VoicemailData?

) : Parcelable {

     companion object {
          internal fun createFrom(sealedCallRecord: SealedCallRecord, keyManager: TelephonyKeyManager): CallRecord {
               val sealedData = sealedCallRecord.sealed().firstOrNull { it.keyId() == keyManager.getKeyId() } ?: throw TelephonyDecryptSealedDataException()
               val decoder = Base64.getDecoder()
               val sealedLocalData = decoder.decode(sealedData.localPhoneNumber())
               val sealedRemoteData = decoder.decode(sealedData.remotePhoneNumber())
               val sealedDuration = decoder.decode(sealedData.durationSeconds())

               // decrypt the sealed data
               val decryptedLocal = keyManager.decryptSealedData(sealedLocalData)
               val decryptedRemote = keyManager.decryptSealedData(sealedRemoteData)
               val decryptedDuration = keyManager.decryptSealedData(sealedDuration)

               val local = String(decryptedLocal, Charset.defaultCharset())
               val remote = String(decryptedRemote, Charset.defaultCharset())
               val duration = ByteBuffer.wrap(decryptedDuration).order(ByteOrder.BIG_ENDIAN).int

               var voicemailData: VoicemailData? = null
               val sealedVoicemailId = sealedCallRecord.voicemailId()
               sealedData.voicemail()?.let { sealedVoicemail ->
                    val sealedVoicemailDuration = decoder.decode(sealedVoicemail.durationSeconds())
                    val decryptedVoicemailDuration = keyManager.decryptSealedData(sealedVoicemailDuration)
                    val voicemailDuration = ByteBuffer.wrap(decryptedVoicemailDuration).order(ByteOrder.BIG_ENDIAN).int
                    voicemailData = VoicemailData(
                         sealedVoicemailId ?: "",
                         voicemailDuration,
                         MediaObject.fromS3Media(sealedVoicemail.media().fragments().s3MediaObject())
                    )
               }

               return CallRecord(sealedCallRecord.id(),
                    sealedCallRecord.owner(),
                    sealedCallRecord.sudoOwner(),
                    sealedCallRecord.phoneNumberId(),
                    sealedCallRecord.direction(),
                    CallRecordState.fromInternalState(sealedCallRecord.state()),
                    Instant.ofEpochMilli(sealedCallRecord.updatedAtEpochMs().toLong()),
                    Instant.ofEpochMilli(sealedCallRecord.createdAtEpochMs().toLong()),
                    local,
                    remote,
                    duration,
                    voicemailData
               )
          }
     }
}

enum class CallRecordState() {
     AUTHORIZED,
     QUEUED,
     RINGING,
     ANSWERED,
     COMPLETED,
     UNANSWERED,
     UNKNOWN;

     companion object {
          internal fun fromInternalState(internalState: CallState): CallRecordState {
               return when (internalState) {
                    CallState.AUTHORIZED -> AUTHORIZED
                    CallState.QUEUED -> QUEUED
                    CallState.RINGING -> RINGING
                    CallState.ANSWERED -> ANSWERED
                    CallState.COMPLETED -> COMPLETED
                    CallState.UNANSWERED -> UNANSWERED
               }
          }
     }
}




