/*
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ijk.player;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TableLayout;
import android.widget.TextView;
import com.ijk.player.media.IRenderView;
import com.ijk.player.media.IVideoViewStateChangeListener;
import com.ijk.player.media.InfoHudViewHolder;
import com.ijk.player.media.MediaPlayerCompat;
import com.ijk.player.media.SurfaceRenderView;
import com.ijk.player.media.TableLayoutBinder;
import com.ijk.player.media.TextureRenderView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.IMediaFormat;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkMediaFormat;

public class IjkVideoView extends FrameLayout implements MediaController.MediaPlayerControl {
    private String TAG = "IjkVideoView";
    private Uri mUri;
    private Map<String, String> mHeaders;
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    public static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;
    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;
    private IRenderView.ISurfaceHolder mSurfaceHolder = null;
    private IMediaPlayer mMediaPlayer = null;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mVideoRotationDegree;
    private IMediaPlayer.OnCompletionListener mOnCompletionListener;
    private IMediaPlayer.OnPreparedListener mOnPreparedListener;
    private int mCurrentBufferPercentage;
    private IMediaPlayer.OnErrorListener mOnErrorListener;
    private IMediaPlayer.OnInfoListener mOnInfoListener;
    private IMediaPlayer.OnBufferingUpdateListener onBufferingUpdateListenerExternal;
    private int mSeekWhenPrepared;
    private boolean mCanPause = true;
    private boolean mCanSeekBack = true;
    private boolean mCanSeekForward = true;

    private Context mAppContext;
    private IRenderView mRenderView;
    private int mVideoSarNum;
    private int mVideoSarDen;

    private InfoHudViewHolder mHudViewHolder;
    private IVideoViewStateChangeListener stateChangeListener;
    private View mMediaBufferingIndicator;

    private static final int FADE_OUT_INFO = 304;//message

    private AudioManager mAudioManager;
    private TextView mInfo;

    private boolean mAndroidPlayerEnable = false;

    public void setAndroidPlayer(boolean isUse) {
        mAndroidPlayerEnable = isUse;
    }

    public void setmAndroidPlayerEnable(boolean mAndroidPlayerEnable) {
        this.mAndroidPlayerEnable = mAndroidPlayerEnable;
    }

    public IjkVideoView(Context context) {
        super(context);
        initVideoView(context);
    }

    public IjkVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initVideoView(context);
    }

    public IjkVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVideoView(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public IjkVideoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initVideoView(context);
    }


    private void initVideoView(Context context) {
        mAppContext = context.getApplicationContext();
        initServicePlayer();
        initRenders();
        mVideoWidth = 0;
        mVideoHeight = 0;
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
    }


    public void setRenderView(IRenderView renderView) {
        if (mRenderView != null) {
            if (mMediaPlayer != null)
                mMediaPlayer.setDisplay(null);

            View renderUIView = mRenderView.getView();
            mRenderView.removeRenderCallback(mSHCallback);
            mRenderView = null;
            removeView(renderUIView);
        }

        if (renderView == null)
            return;

        mRenderView = renderView;
        renderView.setAspectRatio(mCurrentAspectRatio);
        if (mVideoWidth > 0 && mVideoHeight > 0)
            renderView.setVideoSize(mVideoWidth, mVideoHeight);
        if (mVideoSarNum > 0 && mVideoSarDen > 0)
            renderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);

        View renderUIView = mRenderView.getView();
        LayoutParams lp = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        renderUIView.setLayoutParams(lp);
        addView(renderUIView);

        mRenderView.addRenderCallback(mSHCallback);
        mRenderView.setVideoRotation(mVideoRotationDegree);
    }

    public void setRender(int render) {
        switch (render) {
            case RENDER_NONE:
                setRenderView(null);
                break;
            case RENDER_TEXTURE_VIEW: {
                TextureRenderView renderView = new TextureRenderView(getContext());
                if (mMediaPlayer != null) {
                    renderView.getSurfaceHolder().bindToMediaPlayer(mMediaPlayer);
                    renderView.setVideoSize(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
                    renderView.setVideoSampleAspectRatio(mMediaPlayer.getVideoSarNum(), mMediaPlayer.getVideoSarDen());
                    renderView.setAspectRatio(mCurrentAspectRatio);
                }
                setRenderView(renderView);
                break;
            }
            case RENDER_SURFACE_VIEW: {
                SurfaceRenderView renderView = new SurfaceRenderView(getContext());
                setRenderView(renderView);
                break;
            }
            default:
                Log.e(TAG, String.format(Locale.getDefault(), "invalid render %d\n", render));
                break;
        }
    }

    public void setHudView(TableLayout tableLayout) {
        mHudViewHolder = new InfoHudViewHolder(getContext(), tableLayout);
    }

    public void setVideoPath(String path) {
        setVideoURI(Uri.parse(path));
    }

    public void setVideoURI(Uri uri) {
        setVideoURI(uri, null);
    }

    private void setVideoURI(Uri uri, Map<String, String> headers) {
        mUri = uri;
        mHeaders = headers;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            reportStateWillChange(STATE_IDLE);
            if (mHudViewHolder != null)
                mHudViewHolder.setMediaPlayer(null);
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
            AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void openVideo() {
        if (mUri == null || mSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            return;
        }
        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);

        AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        try {
            mMediaPlayer = createPlayer(mAndroidPlayerEnable);
            final Context context = getContext();
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                mMediaPlayer.setDataSource(mAppContext, mUri, mHeaders);
            } else {
                mMediaPlayer.setDataSource(mUri.toString());
            }
            bindSurfaceHolder(mMediaPlayer, mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();

            if (mHudViewHolder != null)
                mHudViewHolder.setMediaPlayer(mMediaPlayer);
            reportStateWillChange(STATE_PREPARING);
            mCurrentState = STATE_PREPARING;
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            return;
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            return;
        } finally {
        }
    }



    private IMediaPlayer.OnErrorListener onErrorListener;
    IMediaPlayer.OnErrorListener mErrorListener = new IMediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(IMediaPlayer mp, int what, int extra) {
            if (onErrorListener != null)
                return onErrorListener.onError(mp, what, extra);
            return false;
        }
    };

    IMediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new IMediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    mVideoSarNum = mp.getVideoSarNum();
                    mVideoSarDen = mp.getVideoSarDen();
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        if (mRenderView != null) {
                            mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                            mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                        }
                        // REMOVED: getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                        requestLayout();
                    }
                }
            };

    IMediaPlayer.OnPreparedListener mPreparedListener = new IMediaPlayer.OnPreparedListener() {
        public void onPrepared(IMediaPlayer mp) {
            mCurrentState = STATE_PREPARED;
            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

            int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                //Log.i("@@@@", "video size: " + mVideoWidth +"/"+ mVideoHeight);
                // REMOVED: getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                if (mRenderView != null) {
                    mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                    mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                    if (!mRenderView.shouldWaitForResize() || mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                        if (mTargetState == STATE_PLAYING) {
                            start();

                        } else if (!isPlaying() &&
                                (seekToPosition != 0 || getCurrentPosition() > 0)) {

                        }
                    }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mTargetState == STATE_PLAYING) {
                    start();
                }
            }
        }
    };

    private IMediaPlayer.OnCompletionListener mCompletionListener =
            new IMediaPlayer.OnCompletionListener() {
                public void onCompletion(IMediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(mMediaPlayer);
                    }
                }
            };

    private IMediaPlayer.OnInfoListener mInfoListener =
            new IMediaPlayer.OnInfoListener() {
                public boolean onInfo(IMediaPlayer mp, int arg1, int arg2) {
                    if (mOnInfoListener != null) {
                        mOnInfoListener.onInfo(mp, arg1, arg2);
                    }
                    switch (arg1) {
                        case IMediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                            Log.d(TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                            Log.d(TAG, "MEDIA_INFO_VIDEO_RENDERING_START:");
                            // shaoheyi 视频一开始渲染我们也把buffer隐藏掉，因为有时不进MEDIA_INFO_BUFFERING_START END
                            // 这个时候我们可能还在显示着缓冲条
                            if (mMediaBufferingIndicator != null)
                                mMediaBufferingIndicator.setVisibility(View.GONE);
                            break;
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_START:");
                            if (mMediaBufferingIndicator != null)
                                mMediaBufferingIndicator.setVisibility(View.VISIBLE);
                            break;
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_END:");
                            if (mMediaBufferingIndicator != null)
                                mMediaBufferingIndicator.setVisibility(View.GONE);
                            break;
                        case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                            Log.d(TAG, "MEDIA_INFO_NETWORK_BANDWIDTH: " + arg2);
                            break;
                        case IMediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                            Log.d(TAG, "MEDIA_INFO_BAD_INTERLEAVING:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                            Log.d(TAG, "MEDIA_INFO_NOT_SEEKABLE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                            Log.d(TAG, "MEDIA_INFO_METADATA_UPDATE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                            Log.d(TAG, "MEDIA_INFO_UNSUPPORTED_SUBTITLE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                            Log.d(TAG, "MEDIA_INFO_SUBTITLE_TIMED_OUT:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                            mVideoRotationDegree = arg2;
                            Log.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED: " + arg2);
                            if (mRenderView != null)
                                mRenderView.setVideoRotation(arg2);
                            break;
                        case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
                            Log.d(TAG, "MEDIA_INFO_AUDIO_RENDERING_START:");
                            break;
                    }
                    return true;
                }
            };

    private IMediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new IMediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(IMediaPlayer mp, int percent) {
                    if (onBufferingUpdateListenerExternal != null) {
                        onBufferingUpdateListenerExternal.onBufferingUpdate(mp, percent);
                    }
                    mCurrentBufferPercentage = percent;
                }
            };

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(IMediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }


    public void setOnCompletionListener(IMediaPlayer.OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    public void setOnErrorListener(IMediaPlayer.OnErrorListener l) {
        onErrorListener = l;
    }

    /**
     * Register a callback to be invoked when an informational event
     * occurs during playback or setup.
     *
     * @param l The callback that will be run
     */
    public void setOnInfoListener(IMediaPlayer.OnInfoListener l) {
        mOnInfoListener = l;
    }

    // REMOVED: mSHCallback
    private void bindSurfaceHolder(IMediaPlayer mp, IRenderView.ISurfaceHolder holder) {
        if (mp == null)
            return;

        if (holder == null) {
            mp.setDisplay(null);
            return;
        }

        holder.bindToMediaPlayer(mp);
    }

    IRenderView.IRenderCallback mSHCallback = new IRenderView.IRenderCallback() {
        @Override
        public void onSurfaceChanged(@NonNull IRenderView.ISurfaceHolder holder, int format, int w, int h) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceChanged: unmatched render callback\n");
                return;
            }

            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState = (mTargetState == STATE_PLAYING);
            boolean hasValidSize = !mRenderView.shouldWaitForResize() || (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }

        @Override
        public void onSurfaceCreated(@NonNull IRenderView.ISurfaceHolder holder, int width, int height) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceCreated: unmatched render callback\n");
                return;
            }

            mSurfaceHolder = holder;
            if (mMediaPlayer != null)
                bindSurfaceHolder(mMediaPlayer, holder);
            else
                openVideo();
        }

        @Override
        public void onSurfaceDestroyed(@NonNull IRenderView.ISurfaceHolder holder) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceDestroyed: unmatched render callback\n");
                return;
            }

            // after we return from this we can't use the surface any more
            mSurfaceHolder = null;
            // REMOVED: if (mMediaController != null) mMediaController.hide();
            // REMOVED: release(true);
            releaseWithoutStop();
        }
    };

    public void releaseWithoutStop() {
        if (mMediaPlayer != null)
            mMediaPlayer.setDisplay(null);
    }

    /*
     * release the media player in any state
     */
    public void release(boolean cleartargetstate) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            // REMOVED: mPendingSubtitleTracks.clear();
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState = STATE_IDLE;
            }
            AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    /**
     * Show text in the info view for "duration" milliseconds
     *
     * @param text
     * @param duration
     */
    private final void showInfo(String text, int duration) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }


    /**
     * 获取window对象
     *
     * @return
     * @throws Exception
     */
    private final Window getWindow() throws Exception {
        Context context = getContext();
        if (context instanceof Activity) {
            return ((Activity) context).getWindow();
        } else {
            throw new Exception("can not get window");
        }
    }

    private final String getString(int resId) {
        return getContext().getResources().getString(resId);
    }


    @Override
    public boolean onTrackballEvent(MotionEvent ev) {

        return false;
    }

    @Override
    public void start() {
        if (isInPlaybackState()) {
            mMediaPlayer.start();
            reportStateWillChange(STATE_PLAYING);
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }


    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                reportStateWillChange(STATE_PAUSED);
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }

    public void suspend() {
        release(false);
    }

    public void resume() {
        openVideo();
    }


    private void reportStateWillChange(int state) {
        if (mCurrentState == state || stateChangeListener == null) {
            return;
        }

        stateChangeListener.doOnVideoViewStateChange(state);
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getDuration();
        }

        return -1;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    public boolean isPaused() {
        return mCurrentState == STATE_PAUSED;
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    private static final int[] s_allAspectRatio = {
            IRenderView.AR_ASPECT_FIT_PARENT,
            IRenderView.AR_ASPECT_FILL_PARENT,
            IRenderView.AR_ASPECT_WRAP_CONTENT,
            IRenderView.AR_MATCH_PARENT,
            IRenderView.AR_16_9_FIT_PARENT,
            IRenderView.AR_4_3_FIT_PARENT};
    private int mCurrentAspectRatio = s_allAspectRatio[0];


    public void setAspectRatio(int mCurrentAspectRatio) {
        this.mCurrentAspectRatio = mCurrentAspectRatio;
        if (mRenderView != null)
            mRenderView.setAspectRatio(mCurrentAspectRatio);
    }

    //-------------------------
    // Extend: Render
    //-------------------------
    public static final int RENDER_NONE = 0;
    public static final int RENDER_SURFACE_VIEW = 1;
    public static final int RENDER_TEXTURE_VIEW = 2;

    private List<Integer> mAllRenders = new ArrayList<Integer>();
    private int mCurrentRenderIndex = 0;
    private int mCurrentRender = RENDER_NONE;

    private void initRenders() {
        mAllRenders.clear();
        if (mAllRenders.isEmpty())
            mAllRenders.add(RENDER_SURFACE_VIEW);
        mCurrentRender = mAllRenders.get(mCurrentRenderIndex);
        setRender(mCurrentRender);
    }

    public int toggleRender() {
        mCurrentRenderIndex++;
        mCurrentRenderIndex %= mAllRenders.size();

        mCurrentRender = mAllRenders.get(mCurrentRenderIndex);
        setRender(mCurrentRender);
        return mCurrentRender;
    }

    @NonNull
    public static String getRenderText(Context context, int render) {
        String text;
        switch (render) {
            case RENDER_NONE:
                text = context.getString(R.string.VideoView_render_none);
                break;
            case RENDER_SURFACE_VIEW:
                text = context.getString(R.string.VideoView_render_surface_view);
                break;
            case RENDER_TEXTURE_VIEW:
                text = context.getString(R.string.VideoView_render_texture_view);
                break;
            default:
                text = context.getString(R.string.N_A);
                break;
        }
        return text;
    }


    public IMediaPlayer createPlayer(boolean isAndroidPlayer) {
        IMediaPlayer mediaPlayer = null;

        IjkMediaPlayer ijkMediaPlayer = null;
        if (mUri != null) {
            if (isAndroidPlayer) {
                AndroidMediaPlayer androidMediaPlayer = new AndroidMediaPlayer();
                mediaPlayer = androidMediaPlayer;
                Log.e("IJKVideoView", "使用本地播放器");
            } else {

                ijkMediaPlayer = new IjkMediaPlayer();
                ijkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_DEBUG);
                ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);//硬解码
                ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 0);//自动旋转；
                ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 1);//使用openGLES
                ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);//色彩格式；
                ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
                ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
                ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 1);
                ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
                mediaPlayer = ijkMediaPlayer;
                Log.e("IJKVideoView", "使用IJK播放器");
            }
        }

        return mediaPlayer;
    }

    //-------------------------
    // Extend: Background
    //-------------------------

    private boolean mEnableBackgroundPlay = false;

    private void initServicePlayer() {
        MediaPlayerService.intentToStart(getContext());
        mMediaPlayer = MediaPlayerService.getMediaPlayer();
        if (mHudViewHolder != null)
            mHudViewHolder.setMediaPlayer(mMediaPlayer);
    }


    public void enterBackground() {
        MediaPlayerService.setMediaPlayer(mMediaPlayer);
    }

    public void stopBackgroundPlay() {
        MediaPlayerService.setMediaPlayer(null);
    }

    //-------------------------
    // Extend: Background
    //-------------------------
    public void showMediaInfo() {
        if (mMediaPlayer == null)
            return;

        int selectedVideoTrack = MediaPlayerCompat.getSelectedTrack(mMediaPlayer, ITrackInfo.MEDIA_TRACK_TYPE_VIDEO);
        int selectedAudioTrack = MediaPlayerCompat.getSelectedTrack(mMediaPlayer, ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);

        TableLayoutBinder builder = new TableLayoutBinder(getContext());
        builder.appendSection(R.string.mi_player);
        builder.appendRow2(R.string.mi_player, MediaPlayerCompat.getName(mMediaPlayer));
        builder.appendSection(R.string.mi_media);
        builder.appendRow2(R.string.mi_resolution, buildResolution(mVideoWidth, mVideoHeight, mVideoSarNum, mVideoSarDen));
        builder.appendRow2(R.string.mi_length, buildTimeMilli(mMediaPlayer.getDuration()));

        ITrackInfo trackInfos[] = mMediaPlayer.getTrackInfo();
        if (trackInfos != null) {
            int index = -1;
            for (ITrackInfo trackInfo : trackInfos) {
                index++;

                int trackType = trackInfo.getTrackType();
                if (index == selectedVideoTrack) {
                    builder.appendSection(getContext().getString(R.string.mi_stream_fmt1, index) + " " + getContext().getString(R.string.mi__selected_video_track));
                } else if (index == selectedAudioTrack) {
                    builder.appendSection(getContext().getString(R.string.mi_stream_fmt1, index) + " " + getContext().getString(R.string.mi__selected_audio_track));
                } else {
                    builder.appendSection(getContext().getString(R.string.mi_stream_fmt1, index));
                }
                builder.appendRow2(R.string.mi_type, buildTrackType(trackType));
                builder.appendRow2(R.string.mi_language, buildLanguage(trackInfo.getLanguage()));

                IMediaFormat mediaFormat = trackInfo.getFormat();
                if (mediaFormat == null) {
                } else if (mediaFormat instanceof IjkMediaFormat) {
                    switch (trackType) {
                        case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO:
                            builder.appendRow2(R.string.mi_codec, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_LONG_NAME_UI));
                            builder.appendRow2(R.string.mi_profile_level, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_PROFILE_LEVEL_UI));
                            builder.appendRow2(R.string.mi_pixel_format, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_PIXEL_FORMAT_UI));
                            builder.appendRow2(R.string.mi_resolution, mediaFormat.getString(IjkMediaFormat.KEY_IJK_RESOLUTION_UI));
                            builder.appendRow2(R.string.mi_frame_rate, mediaFormat.getString(IjkMediaFormat.KEY_IJK_FRAME_RATE_UI));
                            builder.appendRow2(R.string.mi_bit_rate, mediaFormat.getString(IjkMediaFormat.KEY_IJK_BIT_RATE_UI));
                            break;
                        case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO:
                            builder.appendRow2(R.string.mi_codec, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_LONG_NAME_UI));
                            builder.appendRow2(R.string.mi_profile_level, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_PROFILE_LEVEL_UI));
                            builder.appendRow2(R.string.mi_sample_rate, mediaFormat.getString(IjkMediaFormat.KEY_IJK_SAMPLE_RATE_UI));
                            builder.appendRow2(R.string.mi_channels, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CHANNEL_UI));
                            builder.appendRow2(R.string.mi_bit_rate, mediaFormat.getString(IjkMediaFormat.KEY_IJK_BIT_RATE_UI));
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    private String buildResolution(int width, int height, int sarNum, int sarDen) {
        StringBuilder sb = new StringBuilder();
        sb.append(width);
        sb.append(" x ");
        sb.append(height);

        if (sarNum > 1 || sarDen > 1) {
            sb.append("[");
            sb.append(sarNum);
            sb.append(":");
            sb.append(sarDen);
            sb.append("]");
        }

        return sb.toString();
    }

    private String buildTimeMilli(long duration) {
        long total_seconds = duration / 1000;
        long hours = total_seconds / 3600;
        long minutes = (total_seconds % 3600) / 60;
        long seconds = total_seconds % 60;
        if (duration <= 0) {
            return "--:--";
        }
        if (hours >= 100) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    private String buildTrackType(int type) {
        Context context = getContext();
        switch (type) {
            case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO:
                return context.getString(R.string.TrackType_video);
            case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO:
                return context.getString(R.string.TrackType_audio);
            case ITrackInfo.MEDIA_TRACK_TYPE_SUBTITLE:
                return context.getString(R.string.TrackType_subtitle);
            case ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT:
                return context.getString(R.string.TrackType_timedtext);
            case ITrackInfo.MEDIA_TRACK_TYPE_METADATA:
                return context.getString(R.string.TrackType_metadata);
            case ITrackInfo.MEDIA_TRACK_TYPE_UNKNOWN:
            default:
                return context.getString(R.string.TrackType_unknown);
        }
    }

    private String buildLanguage(String language) {
        if (TextUtils.isEmpty(language))
            return "und";
        return language;
    }

    public ITrackInfo[] getTrackInfo() {
        if (mMediaPlayer == null)
            return null;

        return mMediaPlayer.getTrackInfo();
    }

    public void selectTrack(int stream) {
        MediaPlayerCompat.selectTrack(mMediaPlayer, stream);
    }

    public void deselectTrack(int stream) {
        MediaPlayerCompat.deselectTrack(mMediaPlayer, stream);
    }

    public int getSelectedTrack(int trackType) {
        return MediaPlayerCompat.getSelectedTrack(mMediaPlayer, trackType);
    }

    public void setStateChangeListener(IVideoViewStateChangeListener stateChangeListener) {
        this.stateChangeListener = stateChangeListener;
    }

    public void setOnBufferingUpdateListener(IMediaPlayer.OnBufferingUpdateListener onBufferingUpdateListenerExternal) {
        this.onBufferingUpdateListenerExternal = onBufferingUpdateListenerExternal;
    }

    public void setMediaBufferingIndicator(View mediaBufferingIndicator) {
        if (mMediaBufferingIndicator != null)
            mMediaBufferingIndicator.setVisibility(View.GONE);
        mMediaBufferingIndicator = mediaBufferingIndicator;
    }

    public View getMediaBufferingIndicator() {
        return mMediaBufferingIndicator;
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            if (what == FADE_OUT_INFO) {
                fadeOutInfo();
            }
        }
    };

    /**
     * 隐藏信息显示
     */
    private void fadeOutInfo() {
        if (mInfo.getVisibility() == View.VISIBLE) {
            mInfo.startAnimation(AnimationUtils.loadAnimation(this.getContext(), android.R.anim.fade_out));
        }
        mInfo.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
    }


}
