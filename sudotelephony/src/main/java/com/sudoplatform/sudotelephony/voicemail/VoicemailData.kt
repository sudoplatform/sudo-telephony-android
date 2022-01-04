package com.sudoplatform.sudotelephony.voicemail

import android.os.Parcelable
import androidx.annotation.Keep
import com.sudoplatform.sudotelephony.media.MediaObject
import kotlinx.android.parcel.Parcelize

/**
 * Voicemail data belonging to a call record
 */
@Keep
@Parcelize
data class VoicemailData(
    /** Unique ID of the voicemail record */
    val id: String,

    /** The duration of the voicemail recording in seconds */
    val durationSeconds: Int,

    /** The media object that can be used to download the voicemail recording */
    val media: MediaObject
) : Parcelable
