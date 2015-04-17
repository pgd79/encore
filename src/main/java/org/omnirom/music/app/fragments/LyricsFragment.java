/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.omnirom.music.api.chartlyrics.ChartLyricsClient;
import org.omnirom.music.api.common.RateLimitException;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.service.BasePlaybackCallback;

import java.io.IOException;

/**
 * A fragment containing a simple view for lyrics.
 */
public class LyricsFragment extends Fragment {
    private static final String TAG = "LyricsFragment";

    private TextView mTvLyrics;
    private ProgressBar mPbLoading;
    private String mLyrics;
    private Song mCurrentSong;
    private Handler mHandler = new Handler();

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(boolean buffering, Song s) {
            if (mCurrentSong == null || !s.equals(mCurrentSong)) {
                mCurrentSong = s;
                getLyrics(s);
            }
        }
    };

    public static LyricsFragment newInstance() {
        return new LyricsFragment();
    }

    public LyricsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ScrollView root = (ScrollView) inflater.inflate(R.layout.fragment_lyrics, container, false);
        mTvLyrics = (TextView) root.findViewById(R.id.tvLyrics);
        mPbLoading = (ProgressBar) root.findViewById(R.id.pbLoading);

        mCurrentSong = PlaybackProxy.getCurrentTrack();
        if (mCurrentSong != null) {
            getLyrics(mCurrentSong);
        }

        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_LYRICS);
        mainActivity.setContentShadowTop(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        PlaybackProxy.addCallback(mPlaybackCallback);
    }

    @Override
    public void onPause() {
        super.onPause();
        PlaybackProxy.removeCallback(mPlaybackCallback);
    }

    private void getLyrics(final Song song) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                new GetLyricsTask().execute(song);
            }
        });
    }

    private class GetLyricsTask extends AsyncTask<Song, Void, String> {
        private Song mSong;

        @Override
        protected void onPreExecute() {
            mTvLyrics.setVisibility(View.GONE);
            mPbLoading.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Song... params) {
            mSong = params[0];
            Artist artist = ProviderAggregator.getDefault().retrieveArtist(mSong.getArtist(), mSong.getProvider());

            String lyrics = null;
            try {
                lyrics = ChartLyricsClient.getSongLyrics(artist.getName(), mSong.getTitle());
            } catch (IOException e) {
                if (e.getMessage().contains("Connection reset by peer")) {
                    // ChartLyrics API resets connection to throttle fetching. Retry every few
                    // seconds until we get them.
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mSong.equals(mCurrentSong)) {
                                getLyrics(mSong);
                            }
                        }
                    }, 3000);
                    cancel(true);
                } else {
                    Log.e(TAG, "Cannot get lyrics", e);
                }
            } catch (RateLimitException e) {
                Log.e(TAG, "Cannot get lyrics", e);
            }

            return lyrics;
        }

        @Override
        protected void onPostExecute(String s) {
            mLyrics = s;

            if (mTvLyrics != null && mSong.equals(mCurrentSong) && !isCancelled()) {
                mTvLyrics.setVisibility(View.VISIBLE);
                mPbLoading.setVisibility(View.GONE);

                if (s == null) {
                    mTvLyrics.setText(R.string.lyrics_not_found);
                } else {
                    mTvLyrics.setText(s);
                }
            }
        }
    }
}