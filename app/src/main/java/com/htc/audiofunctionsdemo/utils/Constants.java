package com.htc.audiofunctionsdemo.utils;

import android.media.AudioFormat;

/**
 * Created by hw_lee on 2017/10/31.
 */

public class Constants {
    public static class AudioIntentNames {
        public static final String INTENT_PLAYBACK_START_NONOFFLOAD = "audio.htc.com.intent.playback.nonoffload";
        public static final String INTENT_PLAYBACK_START_OFFLOAD = "audio.htc.com.intent.playback.offload";
        public static final String INTENT_PLAYBACK_STOP = "audio.htc.com.intent.playback.stop";
        public static final String INTENT_PLAYBACK_SEEK = "audio.htc.com.intent.playback.seek";
        public static final String INTENT_PLAYBACK_PAUSE_RESUME = "audio.htc.com.intent.playback.pause.resume";

        public static final String INTENT_RECORD_START = "audio.htc.com.intent.record.start";
        public static final String INTENT_RECORD_START_24BIT = "audio.htc.com.intent.record.start24";
        public static final String INTENT_RECORD_STOP = "audio.htc.com.intent.record.stop";

        public static final String INTENT_VOIP_START = "audio.htc.com.intent.voip.start";
        public static final String INTENT_VOIP_STOP = "audio.htc.com.intent.voip.stop";
        public static final String INTENT_VOIP_MUTE_OUTPUT = "audio.htc.com.intent.voip.mute.output";

        public static final String[] INTENT_NAMES = {
                INTENT_PLAYBACK_START_NONOFFLOAD,
                INTENT_PLAYBACK_START_OFFLOAD,
                INTENT_PLAYBACK_STOP,
                INTENT_PLAYBACK_SEEK,
                INTENT_PLAYBACK_PAUSE_RESUME,
                INTENT_RECORD_START,
                INTENT_RECORD_START_24BIT,
                INTENT_RECORD_STOP,
                INTENT_VOIP_START,
                INTENT_VOIP_STOP,
                INTENT_VOIP_MUTE_OUTPUT
        };
    }
    public static class AudioRecordConfig {
        public static final int SAMPLING_RATE = 8000;
        public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
        public static final int NUM_CHANNELS = 1;
        public static final int ENCODING_CONFIG = AudioFormat.ENCODING_PCM_16BIT;
        public static final int NORMALIZATION_FACTOR = 32768;
        public static final int BYTES_PER_ELEMENT = 2; // 2 bytes in 16bit format
        public static final int CIRCULAR_BUFFER_SIZE_MILLIS = -1;
        public static final int BUFFER_SIZE_MILLIS = 100;
    }
    public static class VOIPConfig {
        public static class TX {
            public static final int SAMPLING_RATE = 8000;
            public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
            public static final int NUM_CHANNELS = 1;
            public static final int ENCODING_CONFIG = AudioFormat.ENCODING_PCM_16BIT;
            public static final int NORMALIZATION_FACTOR = 32768;
            public static final int BYTES_PER_ELEMENT = 2; // 2 bytes in 16bit format
            public static final int CIRCULAR_BUFFER_SIZE_MILLIS = 5000;
            public static final int BUFFER_SIZE_MILLIS = 100;
        }
        public static class RX {
            public static final int SAMPLING_RATE = 8000;
            public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
            public static final int NUM_CHANNELS = 2;
            public static final int ENCODING_CONFIG = AudioFormat.ENCODING_PCM_16BIT;
            public static final int NORMALIZATION_FACTOR = 32768;
            public static final int BYTES_PER_ELEMENT = 2; // 2 bytes in 16bit format
            public static final int OUTPUT_TONE_FREQ = 1000;
            public static final int BUFFER_SIZE_MILLIS = 100;
        }
    }
}
