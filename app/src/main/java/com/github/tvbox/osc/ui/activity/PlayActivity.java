package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.controller.BoxVodControlView;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.ui.dialog.ParseDialog;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.util.Map;

import xyz.doikki.videocontroller.component.GestureView;
import xyz.doikki.videoplayer.player.ProgressManager;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
public class PlayActivity extends BaseActivity {
    private VideoView mVideoView;
    private VodController controller;
    private SourceViewModel sourceViewModel;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_play;
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
    }

    private void initView() {
        setLoadSir(findViewById(R.id.rootLayout));
        mVideoView = findViewById(R.id.mVideoView);
        PlayerHelper.updateCfg(mVideoView);
//        ViewGroup.LayoutParams layoutParams = mVideoView.getLayoutParams();
//        layoutParams.width = 100;
//        layoutParams.height = 50;
//        mVideoView.setLayoutParams(layoutParams);

        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int state) {
                switch (state) {
                    case VideoView.STATE_IDLE:
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_PLAYING:
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PAUSED:
                    case VideoView.STATE_BUFFERING:
                    case VideoView.STATE_PREPARING:
                        break;
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        playNext();
                        break;
                    case VideoView.STATE_ERROR:
                        Toast.makeText(mContext, "播放错误", Toast.LENGTH_SHORT).show();
                        finish();
                        tryDismissParse();
                        break;
                }
            }
        });

        controller = new VodController(this);
        controller.setCanChangePosition(true);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        mVideoView.setProgressManager(new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {
                CacheManager.save(MD5.string2MD5(url), progress);
            }

            @Override
            public long getSavedProgress(String url) {
                if (CacheManager.getCache(MD5.string2MD5(url)) == null) {
                    return 0;
                }
                return (long) CacheManager.getCache(MD5.string2MD5(url));
            }
        });
        mVideoView.setVideoController(controller);
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.playResult.observe(this, new Observer<JSONObject>() {
            @Override
            public void onChanged(JSONObject object) {
                showSuccess();
                if (object != null && object.optString("key", "").equals(parseKey)) {
                    String progressKey = object.optString("proKey", null);
                    parseDialog.parse(sourceKey, object, new ParseDialog.ParseCallback() {
                        @Override
                        public void success(String playUrl, Map<String, String> headers) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (mVideoView != null) {
                                        mVideoView.release();
                                        mVideoView.setProgressKey(progressKey);
                                        if (headers != null) {
                                            mVideoView.setUrl(playUrl, headers);
                                        } else {
                                            mVideoView.setUrl(playUrl);
                                        }
                                        mVideoView.start();
                                    }
                                    tryDismissParse();
                                }
                            });
                        }

                        @Override
                        public void fail() {
//                            PlayActivity.this.finish();
//                            tryDismissParse();
                        }
                    });
                }
            }
        });
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            mVodInfo = (VodInfo) bundle.getSerializable("VodInfo");
            sourceKey = bundle.getString("sourceKey");
            play();
        }
    }

    @Override
    public void onBackPressed() {
        if (controller.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            if (controller.onKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null) {
            mVideoView.resume();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) {
            mVideoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        tryDismissParse();
    }

    private VodInfo mVodInfo;
    private String sourceKey;

    private void playNext() {
        boolean hasNext = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasNext = false;
        } else {
            hasNext = mVodInfo.playIndex + 1 < mVodInfo.seriesMap.get(mVodInfo.playFlag).size();
        }
        if (!hasNext) {
            Toast.makeText(this, "已经是最后一集了!", Toast.LENGTH_SHORT).show();
            return;
        }
        mVodInfo.playIndex++;
        play();
    }

    private void playPrevious() {
        boolean hasPre = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasPre = false;
        } else {
            hasPre = mVodInfo.playIndex - 1 >= 0;
        }
        if (!hasPre) {
            Toast.makeText(this, "已经是第一集了!", Toast.LENGTH_SHORT).show();
            return;
        }
        mVodInfo.playIndex--;
        play();
    }

    ParseDialog parseDialog = null;

    void tryDismissParse() {
        if (parseDialog != null) {
            try {
                parseDialog.dismiss();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }


    private volatile String parseKey = null;

    public void play() {
        VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodInfo.playIndex));
        // controller.boxTVRefreshInfo(mVodInfo.name + " " + vs.name + (Hawk.get(HawkConfig.DEBUG_OPEN, false) ? vs.url : ""));

        tryDismissParse();

        parseDialog = new ParseDialog().build(this, new ParseDialog.BackPress() {
            @Override
            public void onBack() {
                PlayActivity.this.finish();
                tryDismissParse();
            }
        });

        parseDialog.show();

        parseKey = vs.url;
        showLoading();
        String progressKey = mVodInfo.sourceKey + mVodInfo.id + mVodInfo.playFlag + mVodInfo.playIndex;
        sourceViewModel.getPlay(sourceKey, mVodInfo.playFlag, progressKey, vs.url);
    }
}