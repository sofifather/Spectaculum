/*
 * Copyright (c) 2014 Mario Guggenberger <mg@protyposis.net>
 *
 * This file is part of MediaPlayer-Extended.
 *
 * MediaPlayer-Extended is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MediaPlayer-Extended is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MediaPlayer-Extended.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.protyposis.android.spectaculum;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.MediaController;

import net.protyposis.android.mediaplayer.MediaPlayer;
import net.protyposis.android.mediaplayer.MediaSource;
import net.protyposis.android.mediaplayer.UriSource;

import java.io.IOException;
import java.util.Map;

/**
 * Created by maguggen on 04.06.2014.
 */
public class MediaPlayerExtendedView extends SpectaculumView implements
        MediaController.MediaPlayerControl {

    private static final String TAG = MediaPlayerExtendedView.class.getSimpleName();

    private static final int STATE_ERROR              = -1;
    private static final int STATE_IDLE               = 0;
    private static final int STATE_PREPARING          = 1;
    private static final int STATE_PREPARED           = 2;
    private static final int STATE_PLAYING            = 3;
    private static final int STATE_PAUSED             = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    private int mCurrentState = STATE_IDLE;
    private int mTargetState  = STATE_IDLE;

    private MediaSource mSource;
    private MediaPlayer mPlayer;
    private int mSeekWhenPrepared;
    private float mPlaybackSpeedWhenPrepared;

    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private MediaPlayer.OnSeekListener mOnSeekListener;
    private MediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener;
    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private MediaPlayer.OnInfoListener mOnInfoListener;

    /**
     * Because this view supplies a surface to the MediaPlayer, not a SurfaceHolder (because it
     * is rendering to a texture instead of the screen), the MediaPlayer cannot handle the screen
     * wake state. To still keep the screen on while playing back the video, MediaPlayer's behavior
     * is reproduced locally in this class.
     */
    private boolean mStayAwake;

    public MediaPlayerExtendedView(Context context) {
        super(context);
    }

    public MediaPlayerExtendedView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setVideoSource(MediaSource source) {
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
        mSource = source;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

    /**
     * @see android.widget.VideoView#setVideoPath(String)
     * @deprecated only for compatibility with Android API
     */
    @Deprecated
    public void setVideoPath(String path) {
        setVideoSource(new UriSource(getContext(), Uri.parse(path)));
    }

    /**
     * @see android.widget.VideoView#setVideoURI(android.net.Uri)
     * @deprecated only for compatibility with Android API
     */
    @Deprecated
    public void setVideoURI(Uri uri) {
        setVideoSource(new UriSource(getContext(), uri));
    }

    /**
     * @see android.widget.VideoView#setVideoURI(android.net.Uri, Map)
     * @deprecated only for compatibility with Android API
     */
    @Deprecated
    public void setVideoURI(Uri uri, Map<String, String> headers) {
        setVideoSource(new UriSource(getContext(), uri, headers));
    }

    private void openVideo() {
        if (mSource == null || getInputHolder().getSurface() == null) {
            // not ready for playback yet, will be called again later
            return;
        }

        release();

        mPlayer = new MediaPlayer();
        mPlayer.setSurface(getInputHolder().getSurface());
        mPlayer.setOnPreparedListener(mPreparedListener);
        mPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
        mPlayer.setOnSeekListener(mSeekListener);
        mPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
        mPlayer.setOnCompletionListener(mCompletionListener);
        mPlayer.setOnErrorListener(mErrorListener);
        mPlayer.setOnInfoListener(mInfoListener);

        // Create a handler for the error message in case an exceptions happens in the following thread
        final Handler exceptionHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                mCurrentState = STATE_ERROR;
                mTargetState = STATE_ERROR;
                mErrorListener.onError(mPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                return true;
            }
        });

        // Set the data source asynchronously as this might take a while, e.g. is data has to be
        // requested from the network/internet.
        // IMPORTANT:
        // We use a Thread instead of an AsyncTask for performance reasons, because threads started
        // in an AsyncTask perform much worse, no matter the priority the Thread gets (unless the
        // AsyncTask's priority is elevated before creating the Thread).
        // See comment in MediaPlayer#prepareAsync for detailed explanation.
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mPlayer.setDataSource(mSource);

                    // Async prepare spawns another thread inside this thread which really isn't
                    // necessary; we call this method anyway because of the events it triggers
                    // when it fails, and to stay in sync which the Android VideoView that does
                    // the same.
                    mPlayer.prepareAsync();
                    mCurrentState = STATE_PREPARING;

                    Log.d(TAG, "video opened");
                } catch (IOException e) {
                    Log.e(TAG, "video open failed", e);

                    // Send message to the handler that an error occurred
                    // (we don't need a message id as the handler only handles this single message)
                    exceptionHandler.sendEmptyMessage(0);
                }
            }
        }).start();
    }

    private void release() {
        if(mPlayer != null) {
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
        stayAwake(false);
    }

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        this.mOnPreparedListener = l;
    }

    public void setOnSeekListener(MediaPlayer.OnSeekListener l) {
        this.mOnSeekListener = l;
    }

    public void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener l) {
        this.mOnSeekCompleteListener = l;
    }

    public void setOnCompletionListener(MediaPlayer.OnCompletionListener l) {
        this.mOnCompletionListener = l;
    }

    public void setOnErrorListener(MediaPlayer.OnErrorListener l) {
        this.mOnErrorListener = l;
    }

    public void setOnInfoListener(MediaPlayer.OnInfoListener l) {
        this.mOnInfoListener = l;
    }

    @Override
    public void start() {
        if(isInPlaybackState()) {
            mPlayer.start();
            stayAwake(true);
        } else {
            mTargetState = STATE_PLAYING;
        }
    }

    @Override
    public void pause() {
        if(isInPlaybackState()) {
            mPlayer.pause();
            stayAwake(false);
        }
        mTargetState = STATE_PAUSED;
    }

    public void stopPlayback() {
        if(mPlayer != null) {
            mPlayer.stop();
            stayAwake(false);
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
        }
    }

    public void setPlaybackSpeed(float speed) {
        if(isInPlaybackState()) {
            mPlayer.setPlaybackSpeed(speed);
        }
        mPlaybackSpeedWhenPrepared = speed;
    }

    public float getPlaybackSpeed() {
        if(isInPlaybackState()) {
            return mPlayer.getPlaybackSpeed();
        } else {
            return mPlaybackSpeedWhenPrepared;
        }
    }

    @Override
    public void onInputSurfaceCreated(InputSurfaceHolder inputSurfaceHolder) {
        openVideo();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        release();
        super.surfaceDestroyed(holder);
    }

    @Override
    public int getDuration() {
        return mPlayer != null ? mPlayer.getDuration() : 0;
    }

    @Override
    public int getCurrentPosition() {
        return mPlayer != null ? mPlayer.getCurrentPosition() : 0;
    }

    @Override
    public void seekTo(int msec) {
        if(isInPlaybackState()) {
            mPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    public MediaPlayer.SeekMode getSeekMode() {
        return mPlayer.getSeekMode();
    }

    public void setSeekMode(MediaPlayer.SeekMode seekMode) {
        mPlayer.setSeekMode(seekMode);
    }

    @Override
    public boolean isPlaying() {
        return mPlayer != null && mPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        return mPlayer != null ? mPlayer.getBufferPercentage() : 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return mPlayer != null ? mPlayer.getAudioSessionId() : 0;
    }

    private void stayAwake(boolean awake) {
        mStayAwake = awake;
        updateSurfaceScreenOn();
    }

    private void updateSurfaceScreenOn() {
        if (getHolder() != null) {
            getHolder().setKeepScreenOn(mStayAwake);
        }
    }

    private boolean isInPlaybackState() {
        return mPlayer != null && mCurrentState >= STATE_PREPARING;
    }

    private MediaPlayer.OnPreparedListener mPreparedListener =
            new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            mCurrentState = STATE_PREPARED;

            setPlaybackSpeed(mPlaybackSpeedWhenPrepared);

            if(mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mp);
            }

            int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }

            if(mTargetState == STATE_PLAYING) {
                start();
            }
        }
    };

    private MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
            updateResolution(width, height);
        }
    };

    private MediaPlayer.OnSeekListener mSeekListener = new MediaPlayer.OnSeekListener() {
        @Override
        public void onSeek(MediaPlayer mp) {
            if(mOnSeekListener != null) {
                mOnSeekListener.onSeek(mp);
            }
        }
    };

    private MediaPlayer.OnSeekCompleteListener mSeekCompleteListener =
            new MediaPlayer.OnSeekCompleteListener() {
        @Override
        public void onSeekComplete(MediaPlayer mp) {
            if(mOnSeekCompleteListener != null) {
                mOnSeekCompleteListener.onSeekComplete(mp);
            }
        }
    };

    private MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            mCurrentState = STATE_PLAYBACK_COMPLETED;
            mTargetState = STATE_PLAYBACK_COMPLETED;
            if(mOnCompletionListener != null) {
                mOnCompletionListener.onCompletion(mp);
            }
            stayAwake(false);
        }
    };

    private MediaPlayer.OnErrorListener mErrorListener =
            new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            if(mOnErrorListener != null) {
                return mOnErrorListener.onError(mp, what, extra);
            }
            return true;
        }
    };

    private MediaPlayer.OnInfoListener mInfoListener =
            new MediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            if(mOnInfoListener != null) {
                return mOnInfoListener.onInfo(mp, what, extra);
            }
            return true;
        }
    };
}
