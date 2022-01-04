package com.sudoplatform.sudotelephony.calllisteners

import androidx.annotation.Keep
import com.twilio.audioswitch.selection.AudioDevice

/**
 * Device that the audio can be routed to
 */
@Keep
enum class VoiceCallAudioDevice {
    BLUETOOTHHEADSET, WIREDHEADSET, EARPIECE, SPEAKERPHONE, UNKNOWN;

    companion object {
        fun fromInternalType(internalType: AudioDevice): VoiceCallAudioDevice {
            return when (internalType) {
                is AudioDevice.BluetoothHeadset -> BLUETOOTHHEADSET
                is AudioDevice.WiredHeadset -> WIREDHEADSET
                is AudioDevice.Earpiece -> EARPIECE
                is AudioDevice.Speakerphone -> SPEAKERPHONE
                else -> UNKNOWN
            }
        }
    }
}
