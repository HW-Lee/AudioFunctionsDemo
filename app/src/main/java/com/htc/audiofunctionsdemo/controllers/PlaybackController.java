package com.htc.audiofunctionsdemo.controllers;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.htc.audiofunctionsdemo.R;
import com.htc.audiofunctionsdemo.utils.Constants;
import com.htc.audiofunctionsdemo.utils.WatchDog;

import java.io.File;
import java.util.Random;

public class PlaybackController implements Controllable {

    public static final boolean MODE_OFFLOAD = true;
    public static final boolean MODE_NONOFFLOAD = false;

    private class PlayerContainer {
        PlaybackController.PlaybackControllerThread mParent;
        MediaPlayer player = null;

        boolean is_offload;
        private Exception e = null;
        private String errorMsg = "";

        PlayerContainer(PlaybackController.PlaybackControllerThread parent) {
            player = new MediaPlayer();
            mParent = parent;
        }


        private void setException(Exception e) {
            this.e = e;
        }

        public void setErrorMsg(String msg) {
            this.errorMsg = msg;
        }

    }

    public class PlaybackControllerThread extends Thread implements WatchDog.Monitor {
        private final String TAG = Constants.packageTag("PlaybackControllerThread");

        // players
        int MAX_PLAYER = 4;
        int offlaod_player;
        private PlayerContainer[] mPlayerContainer;

        // file
        private String fileExtensions[] = {".wav", ".mp3"};
        private final int NONOFFLOADFILE = 0;
        private final int OFFLOADFILE = 1;

        AudioManager mAudioManager;
        final PlaybackController mParent;
        final PlaybackControllerThread wd_lock;
        boolean exitPending;
        int cmd;

        PlaybackControllerThread(AudioManager m, PlaybackController parnet) {
            mAudioManager = m;
            mParent = parnet;
            wd_lock = this;
            exitPending = false;

            mPlayerContainer = new PlayerContainer[MAX_PLAYER];
            for (int i = 0; i < MAX_PLAYER; i++)
                mPlayerContainer[i] = null;
            offlaod_player = -1;
        }

        private void _start(int idx, boolean is_offload) {
            String file_name = mParent.ssrFileName;
            final String function = "start";

            Message msg = commHandler.obtainMessage();
            msg.what = R.id.current_state;
            msg.obj = "State: ";
            msg.obj += function + "++";
            msg.sendToTarget();

            if (idx < 0 || idx >= MAX_PLAYER) {
                Log.d(TAG, "playback error: invalid index for playback " + idx);
                msg = commHandler.obtainMessage();
                msg.what = R.id.current_state;
                msg.obj = "State: ";
                msg.obj += function + "[" + "playback error: invalid index for playback " + idx + "]";
                msg.sendToTarget();
                return;
            }
            if (mPlayerContainer[idx] != null) {
                Log.d(TAG, "player " + idx + " is using");
                msg = commHandler.obtainMessage();
                msg.what = R.id.current_state;
                msg.obj = "State: ";
                msg.obj += function + "[" + "player " + idx + " is using" + "]";
                msg.sendToTarget();
                return;
            }

            File SDCardpath = Environment.getExternalStorageDirectory();
            File playbackFile = new File(SDCardpath.getAbsolutePath() + "/Music/" + file_name);
            Log.d(TAG, "playback idx " + idx + " offload " + is_offload + " file: " + playbackFile.getAbsolutePath());
            msg = commHandler.obtainMessage();
            msg.what = R.id.current_state;
            msg.obj = "State: ";
            msg.obj += function + "[" + "playback idx " + idx + " offload " + is_offload + " file: " + playbackFile.getAbsolutePath() + "]";
            msg.sendToTarget();

            synchronized (mParent) {
                mPlayerContainer[idx] = new PlayerContainer(this);
            }

            mPlayerContainer[idx].player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayerContainer[idx].player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    int idx = -1;
                    for (int i = 0; i < MAX_PLAYER; i++)
                        if (mPlayerContainer[i] != null && mp == mPlayerContainer[i].player)
                            idx = i;

                    if (idx >= 0) {
                        synchronized (mPlayerContainer[idx].mParent.wd_lock) {
                            mp.stop();
                            mp.release();
                            Log.d(TAG, "playback: onCompletion() player idx " + idx + " is End");
                            Message msg = commHandler.obtainMessage();
                            msg.what = R.id.current_state;
                            msg.obj = "State: ";
                            msg.obj += function + "[" + "playback: onCompletion() player idx " + idx + " is End" + "]";
                            msg.sendToTarget();
                        }
                    } else {
                        mp.pause();
                        mp.stop();
                        mp.release();
                        Log.d(TAG, "playback: onCompletion() un-known player End");
                        Message msg = commHandler.obtainMessage();
                        msg.what = R.id.current_state;
                        msg.obj = "State: ";
                        msg.obj += function + "[" + "playback: onCompletion() un-known player End" + "]";
                        msg.sendToTarget();
                    }

                    if (idx >= 0)
                        synchronized (mPlayerContainer[idx].mParent.mParent) {
                            mPlayerContainer[idx] = null;
                        }
                }
            });

            synchronized (this.wd_lock) {
                try {
                    mPlayerContainer[idx].player.setDataSource(playbackFile.getAbsolutePath());
                    mPlayerContainer[idx].is_offload = is_offload;
                    mPlayerContainer[idx].player.prepare();
                    mPlayerContainer[idx].player.start();
                } catch (Exception e) {
                    Log.d(TAG, "playback error:" + e);
                    msg = commHandler.obtainMessage();
                    msg.what = R.id.current_state;
                    msg.obj = "State: ";
                    msg.obj += function + "[playback error]";
                    msg.sendToTarget();
                    e.printStackTrace();
                    mPlayerContainer[idx].setException(e);
                }
            }
        }

        private void _stop(int idx) {
            final String function = "stop";
            Message msg = commHandler.obtainMessage();

            if (idx < 0 || idx >= MAX_PLAYER) {
                Log.d(TAG, "playback stop error: invalid index for playback " + idx);
                msg.what = R.id.current_state;
                msg.obj = "State: ";
                msg.obj += function + "[" + "playback stop error: invalid index for playback " + idx + "]";
                msg.sendToTarget();
                return;
            }
            if (mPlayerContainer[idx] != null) {
                Log.d(TAG, "playback idx " + idx + " stop");
                msg.what = R.id.current_state;
                msg.obj = "State: ";
                msg.obj += function + "[" + "playback idx " + idx + " stop" + "]";
                msg.sendToTarget();
                synchronized (this.wd_lock) {
                    mPlayerContainer[idx].player.pause();
                    mPlayerContainer[idx].player.stop();
                    mPlayerContainer[idx].player.release();
                }
                synchronized (mParent) {
                    mPlayerContainer[idx] = null;
                }
                if (idx == offlaod_player)
                    offlaod_player = -1;
            } else {
                Log.d(TAG, "playback stop idx " + idx + " is null");
                msg.what = R.id.current_state;
                msg.obj = "State: ";
                msg.obj += function + "[" + "playback stop idx " + idx + " is null" + "]";
                msg.sendToTarget();
            }
        }

        private void _seek(int idx) {
            MediaPlayer mPlayer;
            Random ran = new Random();
            int percent = ran.nextInt(99) + 1;

            if (idx < 0 || idx >= MAX_PLAYER) {
                Log.d(TAG, "playback seek error: invalid index for playback " + idx);
                return;
            }

            if (mPlayerContainer[idx] != null) {
                mPlayer = mPlayerContainer[idx].player;
                if (mPlayer.isPlaying()) {
                    int duration = mPlayer.getDuration();
                    duration = (int) ((float) duration * ((float) percent / 100.0));
                    Log.d(TAG, "playback seek to " + duration + "(" + percent + "%)");
                    synchronized (this.wd_lock) {
                        mPlayer.seekTo(duration);
                    }
                }
            } else {
                Log.d(TAG, "playback seek idx " + idx + " is null, start it and seek");
                _start(idx, true);
            }
        }

        private void _pause_resume(int idx) {
            MediaPlayer mPlayer;
            if (idx < 0 || idx >= MAX_PLAYER) {
                Log.d(TAG, "playback pause_resume error: invalid index for playback " + idx);
                return;
            }
            if (mPlayerContainer[idx] != null) {
                mPlayer = mPlayerContainer[idx].player;
                if (mPlayer != null) {
                    if (mPlayer.isPlaying())
                        synchronized (this.wd_lock) {
                            mPlayer.pause();
                        }
                    else {
                        synchronized (this.wd_lock) {
                            int maxStream = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            int streamVol = 15;
                            if (streamVol > maxStream)
                                streamVol = maxStream;
                            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, streamVol, 0);
                            mPlayer.start();
                        }
                    }
                }
            } else
                Log.d(TAG, "playback pause-resume idx " + idx + " is null");
        }

        private void closeAllPlayer() {
            Log.d(TAG, "playback close all player");
            for (int i = 0; i < MAX_PLAYER; i++) {
                if (mPlayerContainer[i] != null) {
                    _stop(i);
                }
            }
        }

        private void setStop() {
            if (this.isAlive()) {
                this.interrupt();
                exitPending = true;
            }
        }

        @Override
        public void monitor() {
            synchronized (this.wd_lock) {
            }
        }

        @Override
        public void run() {
            while (!exitPending) {
                synchronized (mParent) { // class Playback lock
                    cmd = mParent.command;
                    mParent.command = CMD_NONE;
                }

                switch (cmd) {
                    case CMD_NONE:
                        try {
                            sleep(500); //ms
                        } catch (InterruptedException e) {
                        }
                        break;
                    case CMD_START:
                        this._start(mParent.idx, mParent.is_offload);
                        break;
                    case CMD_STOP:
                        this._stop(mParent.idx);
                        break;
                    case CMD_SEEK:
                        this._seek(mParent.idx);
                        break;
                    case CMD_PAUSE_RESUME:
                        this._pause_resume(mParent.idx);
                        break;
                }
                cmd = CMD_NONE;
            }
            closeAllPlayer();
            Log.d(TAG, "playback thraed exit");
        }
    } // end class playback_thread

    private static final String TAG = Constants.packageTag("PlaybackController");

    private int idx;
    private boolean is_offload;
    private String ssrFileName;
    public PlaybackControllerThread thread;

    final private int CMD_NONE = 0;
    final private int CMD_START = 1;
    final private int CMD_STOP = 2;
    final private int CMD_SEEK = 3;
    final private int CMD_PAUSE_RESUME = 4;
    private int command;

    private Handler commHandler;

    public PlaybackController(AudioManager audioManager, Handler handler) {
        idx = -1;
        is_offload = false;
        ssrFileName = "";
        command = CMD_NONE;
        commHandler = handler;

        thread = new PlaybackControllerThread(audioManager, this);
        thread.start();
    }

    /* **************************************
        *        MediaPlayer
        *        TODO:
        *        1. previous/next song feature
        * ***************************************/
    public void start(int idx, boolean is_offload) {
        synchronized (this) { // class PlaybackController lock
            this.idx = idx;
            this.is_offload = is_offload;
            command = CMD_START;
        }
    }

    public void start_name(int idx, boolean is_offload, String name) {
        synchronized (this) { // class PlaybackController lock
            this.ssrFileName = name;
            this.idx = idx;
            this.is_offload = is_offload;
            command = CMD_START;
        }
    }

    public void stop(int idx) {
        synchronized (this) { // class PlaybackController lock
            this.idx = idx;
            command = CMD_STOP;
        }
    }

    public void seek(int idx) {
        synchronized (this) { // class PlaybackController lock
            this.idx = idx;
            command = CMD_SEEK;
        }
    }

    public void pause_resume(int idx) {
        synchronized (this) { // class PlaybackController lock
            this.idx = idx;
            command = CMD_PAUSE_RESUME;
        }
    }

    @Override
    public void destroy() {
        thread.setStop();
    }
}
