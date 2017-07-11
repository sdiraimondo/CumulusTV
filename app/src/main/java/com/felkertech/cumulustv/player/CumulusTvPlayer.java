package com.felkertech.cumulustv.player;

import android.content.Context;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.ExoPlayerLibraryInfo;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.VideoFormatSelectorUtil;
import com.google.android.exoplayer.dash.DashChunkSource;
import com.google.android.exoplayer.dash.DefaultDashTrackSelector;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescriptionParser;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsMasterPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.hls.Variant;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.TextRenderer;
import com.google.android.exoplayer.text.eia608.Eia608TrackRenderer;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.MimeTypes;

import com.google.android.media.tv.companionlibrary.TvPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by guest1 on 12/23/2016.
 */
public class CumulusTvPlayer implements TvPlayer, ExoPlayer.EventListener {
    private static final String TAG = CumulusTvPlayer.class.getSimpleName();
    private static final boolean DEBUG = false;

    private List<Callback> mTvCallbacks = new ArrayList<>();
    private List<ErrorListener> mErrorListeners = new ArrayList<>();
    private SimpleExoPlayer mSimpleExoPlayer;
    private float mPlaybackSpeed;
    private Context mContext;

    public CumulusTvPlayer(Context context) {
        this(context,  new DefaultTrackSelector(), new DefaultLoadControl());
    }

    public CumulusTvPlayer(Context context, TrackSelector trackSelector, LoadControl loadControl) {
        mSimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector, loadControl);
        mContext = context;
        mSimpleExoPlayer.addListener(this);
        mSimpleExoPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
    }

    @Override
    public void seekTo(long position) {
        mSimpleExoPlayer.seekTo(position);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void setPlaybackParams(PlaybackParams params) {
        mSimpleExoPlayer.setPlaybackParams(params);
        mPlaybackSpeed = params.getSpeed();
        if (DEBUG) {
            Log.d(TAG, "Set params " + params.toString());
        }
    }

    public float getPlaybackSpeed() {
        return mPlaybackSpeed;
    }

    @Override
    public long getCurrentPosition() {
        return mSimpleExoPlayer.getBufferedPosition();
    }

    @Override
    public long getDuration() {
        return mSimpleExoPlayer.getDuration();
    }

    @Override
    public void setSurface(Surface surface) {
        mSimpleExoPlayer.setVideoSurface(surface);
    }

    @Override
    public void setVolume(float volume) {
        mSimpleExoPlayer.setVolume(volume);
    }

    @Override
    public void pause() {
        mSimpleExoPlayer.setPlayWhenReady(false);
    }

    @Override
    public void play() {
        mSimpleExoPlayer.setPlayWhenReady(true);
    }

    @Override
    public void registerCallback(Callback callback) {
        mTvCallbacks.add(callback);
    }

    @Override
    public void unregisterCallback(Callback callback) {
        mTvCallbacks.remove(callback);
    }

    public void registerErrorListener(ErrorListener callback) {
        mErrorListeners.add(callback);
    }

    public void unregisterErrorListener(ErrorListener callback) {
        mErrorListeners.remove(callback);
    }

    public void startPlaying(Uri mediaUri) {
        // This is the MediaSource representing the media to be played.
        try {
            MediaSource videoSource = MediaSourceFactory.getMediaSourceFor(mContext, mediaUri);
            // Prepare the player with the source.
            mSimpleExoPlayer.prepare(videoSource);
        } catch (MediaSourceFactory.NotMediaException e) {
            for (ErrorListener listener : mErrorListeners) {
                listener.onError(e);
            }
        }
    }

    public void stop() {
        mSimpleExoPlayer.stop();
    }

    public void release() {
        mSimpleExoPlayer.removeListener(this);
        mSimpleExoPlayer.release();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        for (Callback tvCallback : mTvCallbacks) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                tvCallback.onCompleted();
            } else if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
                tvCallback.onStarted();
            }
        }
        Log.d(TAG, "Player state changed to " + playbackState + ", PWR: " + playWhenReady);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        for (Callback tvCallback : mTvCallbacks) {
            tvCallback.onError();
        }
        for (ErrorListener listener : mErrorListeners) {
            listener.onError(error);
        }
    }

    @Override
    public void onPositionDiscontinuity() {
    }

    public interface ErrorListener {
        void onError(Exception error);
    }
}
