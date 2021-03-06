package com.sudoplatform.sudotelephony

import android.os.Parcelable
import com.sudoplatform.sudotelephony.fragment.S3MediaObject
import com.sudoplatform.sudotelephony.type.Direction
import com.sudoplatform.sudotelephony.type.MessageState
import kotlinx.android.parcel.Parcelize
import java.time.Instant

/**
 * A message that has been either received or sent by the user.
 */
@Parcelize
data class PhoneMessage(
    val id: String,
    val owner: String,
    val conversation: String,
    val updated: Instant,
    val created: Instant,
    val local: String,
    val remote: String,
    val body: String,
    val direction: Direction,
    val state: MessageState,
    val media: List<MediaObject> = emptyList()
) : Parcelable

/**
 * A remote media object
 */
@Parcelize
data class MediaObject (
    val key: String,
    val bucket: String,
    val region: String) : Parcelable {
    internal companion object {
        fun fromS3Media(media: S3MediaObject): MediaObject {
            return MediaObject(media.key(), media.bucket(), media.region())
        }
    }
}



