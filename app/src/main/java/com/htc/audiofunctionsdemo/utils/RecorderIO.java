package com.htc.audiofunctionsdemo.utils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class RecorderIO extends Thread {
    private static final String TAG = Constants.packageTag("RecorderIO");

    private int mSampleRate;
    private int mChannelConfig;
    private int mNumChannels;
    private int mEncodingConfig;
    private int mBytesPerElement;

    // Thread
    private Thread handle = null;
    private Runnable content;
    private AudioRecord mRecorder;
    private int mBufferSize = 0;
    private boolean isTerminated = false;
    private int mBufferMillis = 0;

    // error
    public Exception excep = null;
    public String errorMsg = "";

    private RecorderIOListener mListener = null;

    public RecorderIO() {
        createRecordContent();
        excep = null;
        errorMsg = "";
    }

    public RecorderIO(int bufMillis) {
        this(bufMillis, false);
    }

    public RecorderIO(int bufMillis, boolean isHD) {
        if (bufMillis > 0)
            mBufferMillis = bufMillis;
        createRecordContent();
        excep = null;
        errorMsg = "";

        if (isHD) {
            mSampleRate = Constants.AudioRecordConfig.SAMPLING_RATE_HD;
            mChannelConfig = Constants.AudioRecordConfig.CHANNEL_CONFIG_HD;
            mNumChannels = Constants.AudioRecordConfig.NUM_CHANNELS_HD;
            mEncodingConfig = Constants.AudioRecordConfig.ENCODING_CONFIG_HD;
            mBytesPerElement = Constants.AudioRecordConfig.BYTES_PER_ELEMENT_HD;
        } else {
            mSampleRate = Constants.AudioRecordConfig.SAMPLING_RATE;
            mChannelConfig = Constants.AudioRecordConfig.CHANNEL_CONFIG;
            mNumChannels = Constants.AudioRecordConfig.NUM_CHANNELS;
            mEncodingConfig = Constants.AudioRecordConfig.ENCODING_CONFIG;
            mBytesPerElement = Constants.AudioRecordConfig.BYTES_PER_ELEMENT;
        }
    }

    public void setRecorderIOListener(RecorderIOListener listener) {
        mListener = listener;
    }

    private void createRecordContent()
    {
        content = new Runnable() {
            @Override
            public void run() {
                int ret;
                byte data[];
                int limit = -1;

                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                mBufferSize = AudioRecord.getMinBufferSize(mSampleRate,
                        mChannelConfig, mEncodingConfig);
                if (mBufferMillis*mSampleRate/1000*mNumChannels*mBytesPerElement > mBufferSize)
                    mBufferSize = mBufferMillis*mSampleRate/1000*mNumChannels*mBytesPerElement;
                Log.d(TAG, "thread start, read buffer size=" + mBufferSize);
                data = new byte[mBufferSize];

                try {
                    mRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                            mSampleRate, mChannelConfig,
                            mEncodingConfig, mBufferSize);
                }
                catch(Exception e) {
                    Log.d(TAG, "Exception is got on the initialization of AudioRecord");
                    e.printStackTrace();
                    excep = e;
                }
                try {
                    ret = mRecorder.getState();

                    if (ret == AudioRecord.STATE_UNINITIALIZED) {
                        Log.d(TAG, "AudioRecord init fail!");
                        errorMsg = "AudioRecord init fail";
                        isTerminated = true;
                    } else {
                        mRecorder.startRecording();
                    }

                    while(!isTerminated) {
                        ret = mRecorder.read(data, 0, mBufferSize);
                        if (ret == AudioRecord.ERROR_INVALID_OPERATION || ret == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "RecordIO error reading audio data! force stop AudioRecord and thread");
                            errorMsg = "AudioRecord read data error";
                            isTerminated = true;
                        } else {
                            if (mListener != null)
                                mListener.onDataRead(data, mBytesPerElement, mNumChannels);
                        }
                        Thread.sleep(20);
                    }
                }
                catch(Exception e) {
                    Log.d(TAG, "thread got exception, stop recorderIO thread");
                    e.printStackTrace();
                    excep = e;
                }
                finally {
                    doWorkBeforeShutdown();
                }
            }
        };
    }

    private void terminate() {
        isTerminated = true;
        interrupt();
    }

    private void doWorkBeforeShutdown() {
        Log.d(TAG, "thread stop");
        if (mRecorder != null) {
            if (mRecorder.getState() == AudioRecord.STATE_INITIALIZED)
                mRecorder.stop();
            else
                Log.w(TAG, "The AudioRecord has not been initialized yet, skip the command AudioRecord.stop().");
            mRecorder.release();
        }
    }

    // convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;

    }

    public void startRecord() {
        isTerminated = false;
        handle = new Thread(content, "AATRecordThread");
        handle.start();
    }

    public void stopRecord() {
        if (handle != null) {
            Log.d(TAG, "stop");
            this.terminate();

            while (handle.isAlive()) ;
            handle = null;
            Log.d(TAG, "stoped");
        }
    }

    public interface RecorderIOListener {
        void onDataRead(byte[] data, int bytesPerSample, int numChannels);
    }
}
