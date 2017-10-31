package com.htc.audiofunctionsdemo;

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
    private static final String TAG = "RecorderIO";
    private static final int RECORDER_BPP = 16;
    private static final int RECORDER_SAMPLERATE = Constants.AudioRecordConfig.SAMPLING_RATE;
    private static final int RECORDER_CHANNELS = Constants.AudioRecordConfig.CHANNEL_CONFIG;
    private static final int NUM_CHANNELS = Constants.AudioRecordConfig.NUM_CHANNELS;
    private static final int RECORDER_AUDIO_ENCODING = Constants.AudioRecordConfig.ENCODING_CONFIG;
    private static final int BytesPerElement = Constants.AudioRecordConfig.BYTES_PER_ELEMENT; // 2 bytes in 16bit format

    private String filePcmPath = Environment.getExternalStorageDirectory() + "/Music/record.pcm";
    private String fileWavPath = Environment.getExternalStorageDirectory() + "/Music/record.wav";

    // Thread
    private Thread handle = null;
    private Runnable content;
    private AudioRecord mRecorder;
    private int mBufferSize = 0;
    private boolean isTerminated = false;
    private int duration = 0;
    private int mBufferMillis = 0;

    // error
    public Exception excep = null;
    public String errorMsg = "";
    private Handler mCallbackErrorHandle = null;

    private BufferedOutputStream f = null;
    private CircualrBuffer buf = null;
    private RecorderIOListener mListerner = null;

    public RecorderIO() {
        duration = 0;
        createRecordContent();
        excep = null;
        errorMsg = "";
    }

    public RecorderIO(int ms, int bufMillis) {
        if (ms > 0)
            duration = ms;
        if (bufMillis > 0)
            mBufferMillis = bufMillis;
        createRecordContent();
        excep = null;
        errorMsg = "";
    }

    public void setRecorderIOListener(RecorderIOListener listener) {
        mListerner = listener;
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
                mBufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                        RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
                if (mBufferMillis*RECORDER_SAMPLERATE/1000*NUM_CHANNELS*BytesPerElement > mBufferSize)
                    mBufferSize = mBufferMillis*RECORDER_SAMPLERATE/1000*NUM_CHANNELS*BytesPerElement;
                Log.d(TAG, "thread start, read buffer size=" + mBufferSize);
                data = new byte[mBufferSize];

                if (duration > 0) {
                    limit = duration / 1000 * RECORDER_SAMPLERATE * 2 / mBufferSize;
                    Log.d(TAG, "circualr buffer size " + limit);
                    buf = new CircualrBuffer(limit, mBufferSize);
                }

                try {
                    deleteFile(filePcmPath);
                    if (buf == null)
                        f = new BufferedOutputStream(new FileOutputStream(filePcmPath));
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "file not found exception, stop recordIO thread");
                    isTerminated = true;
                    e.printStackTrace();
                    excep = e;
                    return;
                }

                try {
                    mRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                            RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                            RECORDER_AUDIO_ENCODING, mBufferSize);
                    ret = mRecorder.getState();
                    if (ret == AudioRecord.STATE_UNINITIALIZED) {
                        Log.d(TAG, "AudioRecord init fail!");
                        errorMsg = "AudioRecord init fail";
                        isTerminated = true;
                    }
                    mRecorder.startRecording();

                    while(!isTerminated) {
                        ret = mRecorder.read(data, 0, mBufferSize);
                        if (ret == AudioRecord.ERROR_INVALID_OPERATION || ret == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "RecordIO error reading audio data! force stop AudioRecord and thread");
                            errorMsg = "AudioRecord read data error";
                            isTerminated = true;
                        } else {
                            if (mListerner != null)
                                mListerner.onDataRead(data, BytesPerElement, NUM_CHANNELS);
                            if(limit != -1 && buf != null){
                                buf.add(data);
                            } else if (f != null){
                                f.write(data, 0, ret);
                            }
                        }
                        Thread.sleep(20);
                    }
                }
                catch(InterruptedException | IOException e) {
                    Log.d(TAG, "thread got exception, stop recorderIO thread");
                    e.printStackTrace();
                    excep = e;
                }
                finally {
                    doWorkBeforeShutdown();
                }

                if ((excep != null || !errorMsg.equals("")) && mCallbackErrorHandle != null) {
                    Message msg = mCallbackErrorHandle.obtainMessage();
                    msg.what = 1;
                    msg.sendToTarget();
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
        mRecorder.stop();
        mRecorder.release();
        try {
            if (f != null) {
                f.close();
                f = null;
            }
            if (buf != null) {
                buf.dump(filePcmPath);
                buf = null;
            }
        } catch (IOException e) {
            Log.d(TAG, "doWorkBeforeShutdown file stream close exception!");
            e.printStackTrace();
            excep = e;
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

    private void deleteFile(String f)
    {
        File file = new File(f);
        file.delete();
    }

    public void startRecord(String path, Handler callback) {
        if (handle == null) {
            if (callback != null)
                mCallbackErrorHandle = callback;

            Log.d(TAG, "start, path =  " + path);
            filePcmPath = path + ".pcm";
            fileWavPath = path + ".wav";

            isTerminated = false;
            handle = new Thread(content, "AATRecordThread");
            handle.start();
        }
    }

    public void stopRecord() {
        if (handle != null) {
            Log.d(TAG, "stop");
            this.terminate();

            while (handle.isAlive()) ;
            handle = null;
            deleteFile(fileWavPath);
            copyWaveFile(filePcmPath, fileWavPath);
            Log.d(TAG, "stoped");
        }
    }

    public void transTxToWav() {
        buf.dump(filePcmPath);
        copyWaveFile(filePcmPath, fileWavPath);
    }

    private void copyWaveFile(String inFilename, String outFilename)
    {
        Log.d(TAG, "copy file to wav");
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 2;
        byte[] data = new byte[mBufferSize];
        if (RECORDER_CHANNELS == AudioFormat.CHANNEL_IN_MONO)
            channels = 1;

        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;
        try
        {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            Log.d(TAG, "File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while (in.read(data) != -1)
            {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException
    {

        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 16 / 8); // block align
        header[33] = 0;
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    private class CircualrBuffer {
        int buffer_size;
        int size = 0;
        int limit = 0;
        int boundary;
        byte buffer[];

        CircualrBuffer(int lim, int buf_size) {
            limit = lim;
            buffer_size = buf_size;
            boundary = limit * buffer_size;
            buffer = new byte[boundary];
        }

        int add(byte [] buf) {
            if (limit > 0) {
                for(int i = 0; i < buffer_size; i++)
                    buffer[(size + i) % boundary] = buf[i];
                size = (size + buffer_size) % boundary;
            }
            return 0;
        }

        private void deleteFile(String f)
        {
            File file = new File(f);
            file.delete();
        }

        void dump(String path) {
            try {
                deleteFile(path);
                BufferedOutputStream f = new BufferedOutputStream(new FileOutputStream(path));
                f.write(buffer, 0, boundary);
                f.close();
                Log.d(TAG, "save file to pcm");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public interface RecorderIOListener {
        void onDataRead(byte[] data, int bytesPerSample, int numChannels);
    }
}
