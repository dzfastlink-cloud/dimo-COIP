package com.receiver.iptv;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

@SuppressWarnings({"deprecation", "UnsafeOptInUsageError"})
public class MainActivity extends Activity {
    private FrameLayout root;
    private SurfaceView sv;
    private WebView w;
    private ExoPlayer player;
    private ValueCallback<Uri[]> fcb;
    private String aspect = "contain";
    private int vw = 0, vh = 0;
    private float par = 1f;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        sv = new SurfaceView(this);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        w = new WebView(this);
        w.setBackgroundColor(Color.TRANSPARENT);
        root.addView(w, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int bt, int ol, int ot, int or, int ob) {
                layoutVideo();
            }
        });

        initPlayer();

        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        w.setWebViewClient(new WebViewClient());
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fcb != null) fcb.onReceiveValue(null);
                fcb = cb;
                try {
                    startActivityForResult(p.createIntent(), 1);
                } catch (Exception e) {
                    fcb = null;
                    cb.onReceiveValue(null);
                    js("window.wToast&&wToast('لا يوجد مدير ملفات على الجهاز')");
                }
                return true;
            }
        });
        w.addJavascriptInterface(new Bridge(), "Android");
        w.loadUrl("file:///android_asset/index.html");
        w.requestFocus();
    }

    private void initPlayer() {
        DefaultHttpDataSource.Factory dsf = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(15000)
                .setUserAgent("Mozilla/5.0 (Linux; Android) WasslaTV");
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dsf))
                .build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true);
        player.setVideoSurfaceView(sv);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean playing) {
                if (playing) notifyJs("playing");
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && player.getPlayWhenReady()) notifyJs("playing");
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                notifyJs("error");
            }

            @Override
            public void onVideoSizeChanged(VideoSize v) {
                vw = v.width;
                vh = v.height;
                par = v.pixelWidthHeightRatio > 0 ? v.pixelWidthHeightRatio : 1f;
                layoutVideo();
            }
        });
    }

    private void js(String code) {
        if (w != null) w.evaluateJavascript(code, null);
    }

    private void notifyJs(String ev) {
        js("window.onPlayer&&onPlayer('" + ev + "')");
    }

    private void layoutVideo() {
        int cw = root.getWidth(), chh = root.getHeight();
        if (vw <= 0 || vh <= 0 || cw <= 0 || chh <= 0) return;
        float va = vw * par / vh, ca = (float) cw / chh;
        int w2, h2;
        if ("fill".equals(aspect)) {
            w2 = cw;
            h2 = chh;
        } else if ("cover".equals(aspect)) {
            if (va > ca) { h2 = chh; w2 = (int) (chh * va); } else { w2 = cw; h2 = (int) (cw / va); }
        } else {
            if (va > ca) { w2 = cw; h2 = (int) (cw / va); } else { h2 = chh; w2 = (int) (chh * va); }
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w2, h2, Gravity.CENTER);
        sv.setLayoutParams(lp);
    }

    private class Bridge {
        @JavascriptInterface
        public void play(final String url) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        player.setMediaItem(MediaItem.fromUri(url));
                        player.prepare();
                        player.setPlayWhenReady(true);
                    } catch (Exception e) {
                        notifyJs("error");
                    }
                }
            });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(new Runnable() {
                public void run() {
                    player.stop();
                    player.clearMediaItems();
                }
            });
        }

        @JavascriptInterface
        public void setAspect(final String m) {
            runOnUiThread(new Runnable() {
                public void run() {
                    aspect = m;
                    layoutVideo();
                }
            });
        }

        @JavascriptInterface
        public String saveText(String name, String text) {
            try {
                byte[] data = text.getBytes("UTF-8");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download");
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (u == null) return "";
                    OutputStream os = getContentResolver().openOutputStream(u);
                    os.write(data);
                    os.close();
                    return "Download/" + name;
                } else {
                    File f = new File(getExternalFilesDir(null), name);
                    FileOutputStream os = new FileOutputStream(f);
                    os.write(data);
                    os.close();
                    return f.getAbsolutePath();
                }
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void openUrl(String u) {
            if (u == null || !(u.startsWith("http://") || u.startsWith("https://"))) return;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
            } catch (Exception e) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        js("window.wToast&&wToast('لا يوجد متصفح على الجهاز')");
                    }
                });
            }
        }
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent d) {
        if (rq == 1 && fcb != null) {
            fcb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d));
            fcb = null;
        } else super.onActivityResult(rq, rs, d);
    }

    @Override
    public void onBackPressed() {
        w.evaluateJavascript("window.onBack?window.onBack():false", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String r) {
                if (!"true".equals(r)) finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) player.play();
    }

    @Override
    protected void onStop() {
        if (player != null) player.pause();
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        if (w != null) w.destroy();
        super.onDestroy();
    }
}
