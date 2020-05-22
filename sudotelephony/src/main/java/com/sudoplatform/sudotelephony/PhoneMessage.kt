package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.fragment.S3MediaObject
import com.sudoplatform.sudotelephony.type.MessageDirection
import com.sudoplatform.sudotelephony.type.MessageState
import java.time.Instant

/**
 * A message that has been either received or sent by the user.
 */
data class PhoneMessage(
    val id: String,
    val owner: String,
    val conversation: String,
    val updated: Instant,
    val created: Instant,
    val local: String,
    val remote: String,
    val body: String,
    val direction: MessageDirection,
    val state: MessageState,
    val media: List<MediaObject> = emptyList())

/**
 * A remote media object
 */
data class MediaObject (
    val key: String,
    val bucket: String,
    val region: String) {
    internal companion object {
        fun fromS3Media(media: S3MediaObject): MediaObject {
            return MediaObject(media.key(), media.bucket(), media.region())
        }
    }
}



