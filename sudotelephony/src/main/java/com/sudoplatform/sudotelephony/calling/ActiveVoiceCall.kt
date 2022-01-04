package com.sudoplatform.sudotelephony.calling

import android.content.Context
import com.sudoplatform.sudotelephony.calllisteners.ActiveCallListener
import com.sudoplatform.sudotelephony.calllisteners.VoiceCallAudioDevice
import com.twilio.audioswitch.selection.AudioDevice
import com.twilio.audioswitch.selection.AudioDeviceSelector
import java.util.UUID

/**
 * An object representing an active voice call. It is used to monitor call events as well as accept input for muting, disconnecting and placing the call on speaker.
 */
class ActiveVoiceCall(
    val context: Context,
    internal val accessToken: String,
    /**
     * The E164-formatted local phone number participating in this voice call.
     */
    val localPhoneNumber: String,
    /**
     * The E164-formatted remote phone number participating in this voice call.
     */
    val remotePhoneNumber: String,
    val callId: UUID,
    private val vendorCall: CallingVendorCall,
    private val listener: ActiveCallListener
) {

    private val audioDeviceSelector: AudioDeviceSelector = AudioDeviceSelector(context)

    init {
        audioDeviceSelector.start { _, selectedAudioDevice ->
            selectedAudioDevice?.let { selectedDevice ->
                listener.activeVoiceCallDidChangeAudioDevice(
                    this,
                    VoiceCallAudioDevice.fromInternalType(selectedDevice)
                )
            }
        }
    }

    internal fun stopAudioDeviceListener() {
        audioDeviceSelector.stop()
    }

    /**
     * Whether outgoing call audio is muted
     */
    var isMuted: Boolean = false
        internal set(value) {
            listener.activeVoiceCallDidChangeMuteState(this, value)
        }

    /**
     * Whether call audio is being routed through the speakers
     */
    val isOnSpeaker: Boolean
        get() {
            return audioDeviceSelector.selectedAudioDevice is AudioDevice.Speakerphone
        }

    /**
     * Disconnects the active call.
     */
    fun disconnect(exception: Exception?) {
        vendorCall.disconnect()
    }

    /**
     * Sets whether outgoing call audio should be muted.
     * @param muted If true, outgoing call audio should be muted.
     */
    fun setMuted(muted: Boolean) {
        vendorCall.isMuted = muted
        isMuted = muted
    }

    /**
     * Sets whether outgoing call audio should be routed through the speakers.
     * @param speaker If true, audio should be routed through the speakers.
     */
    fun setAudioOutputToSpeaker(speaker: Boolean) {
        val devices: List<AudioDevice> = audioDeviceSelector.availableAudioDevices
        if (speaker) {
            devices.find { it is AudioDevice.Speakerphone }?.let {
                audioDeviceSelector.selectDevice(it)
                audioDeviceSelector.activate()
            }
        } else {
            // go back to prvious device
            audioDeviceSelector.deactivate()
        }
    }
}
