package com.nauz.voicerecorder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_AUDIO = 101;
    private static final int REQ_WRITE = 102;

    private static final int C_TEXT = 0xFFF2F2F7;
    private static final int C_MUTED = 0xFF8E8EA6;
    private static final int C_RED = 0xFFFF4D5E;

    private WaveformView wave;
    private TextView timer;
    private TextView status;
    private TextView count;
    private TextView empty;
    private LinearLayout list;
    private View recBtn;
    private View recInner;
    private Button pauseBtn;
    private Button discardBtn;

    private MediaRecorder recorder;
    private File currentFile;
    private long startedAt = 0L;
    private long elapsed = 0L;
    private boolean isPaused = false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable ticker;

    private MediaPlayer player;
    private File playingFile;
    private View playingRow;

    private final ArrayList<File> files = new ArrayList<File>();
    private File pendingExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wave = (WaveformView) findViewById(R.id.wave);
        timer = (TextView) findViewById(R.id.timer);
        status = (TextView) findViewById(R.id.status);
        count = (TextView) findViewById(R.id.count);
        empty = (TextView) findViewById(R.id.empty);
        list = (LinearLayout) findViewById(R.id.list);
        recBtn = findViewById(R.id.recBtn);
        recInner = findViewById(R.id.recInner);
        pauseBtn = (Button) findViewById(R.id.pauseBtn);
        discardBtn = (Button) findViewById(R.id.discardBtn);

        recBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRecordClick();
            }
        });

        pauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePause();
            }
        });

        discardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                discardTake();
            }
        });

        loadFiles();
    }

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    private File recDir() {
        File base = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (base == null) base = getFilesDir();
        File dir = new File(base, "Voice Recorder");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void loadFiles() {
        files.clear();
        File[] arr = recDir().listFiles();
        if (arr != null) {
            for (File f : arr) {
                if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".m4a")) {
                    files.add(f);
                }
            }
        }
        files.sort(new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        renderList();
    }

    private void renderList() {
        list.removeAllViews();
        count.setText(String.valueOf(files.size()));
        empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final File f : files) {
            View row = inflater.inflate(R.layout.item_recording, list, false);
            TextView name = (TextView) row.findViewById(R.id.rowName);
            TextView detail = (TextView) row.findViewById(R.id.rowDetail);
            Button play = (Button) row.findViewById(R.id.rowPlay);
            Button export = (Button) row.findViewById(R.id.rowExport);
            Button delete = (Button) row.findViewById(R.id.rowDelete);

            name.setText(titleFor(f));
            detail.setText(fmt(durationOf(f)) + "  ·  " + size(f.length()));

            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePlay(f, (View) v.getParent());
                }
            });
            export.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    export(f);
                }
            });
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(f);
                }
            });

            list.addView(row);
        }
    }

    private String titleFor(File f) {
        return new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                .format(new Date(f.lastModified()));
    }

    private long durationOf(File f) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? 0L : Long.parseLong(d);
        } catch (Exception e) {
            return 0L;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    private String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / 1048576.0);
    }

    private String fmt(long ms) {
        long total = ms / 1000L;
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    // ------------------------------------------------------------------
    // recording
    // ------------------------------------------------------------------

    private void onRecordClick() {
        if (recorder != null) {
            stopRecording();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startRecording();
    }

    private void startRecording() {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        currentFile = new File(recDir(), "REC_" + stamp + ".m4a");

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                recorder = new MediaRecorder(this);
            } else {
                recorder = new MediaRecorder();
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            releaseRecorder();
            if (currentFile != null && currentFile.exists()) currentFile.delete();
            currentFile = null;
            toast("Could not start recording (" + e.getClass().getSimpleName() + ")");
            return;
        }

        isPaused = false;
        startedAt = System.currentTimeMillis();
        elapsed = 0L;

        recBtn.setBackgroundResource(R.drawable.bg_rec_active);
        recInner.setBackgroundResource(R.drawable.bg_inner_active);
        pauseBtn.setEnabled(true);
        pauseBtn.setText("\u23F8");
        discardBtn.setEnabled(true);
        status.setText("RECORDING");
        status.setTextColor(C_RED);
        wave.setLive(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startTicker();
    }

    private void startTicker() {
        stopTicker();
        ticker = new Runnable() {
            @Override
            public void run() {
                if (recorder == null) return;
                if (!isPaused) {
                    elapsed = System.currentTimeMillis() - startedAt;
                    timer.setText(fmt(elapsed));
                    try {
                        int amp = recorder.getMaxAmplitude();
                        float v = (float) Math.sqrt(Math.min(1.0, amp / 22000.0));
                        wave.pushLevel(v);
                    } catch (Exception ignored) {
                    }
                }
                ui.postDelayed(this, 90);
            }
        };
        ui.post(ticker);
    }

    private void stopTicker() {
        if (ticker != null) {
            ui.removeCallbacks(ticker);
            ticker = null;
        }
    }

    private void togglePause() {
        if (recorder == null) return;
        try {
            if (!isPaused) {
                recorder.pause();
                isPaused = true;
                pauseBtn.setText("\u25B6");
                status.setText("PAUSED");
                status.setTextColor(C_MUTED);
            } else {
                recorder.resume();
                isPaused = false;
                startedAt = System.currentTimeMillis() - elapsed;
                pauseBtn.setText("\u23F8");
                status.setText("RECORDING");
                status.setTextColor(C_RED);
            }
        } catch (Exception e) {
            toast("Pause not supported on this device");
        }
    }

    private void stopRecording() {
        if (recorder == null) return;
        boolean ok = true;
        try {
            recorder.stop();
        } catch (Exception e) {
            ok = false;
        }
        releaseRecorder();

        File f = currentFile;
        currentFile = null;
        long dur = elapsed;

        resetRecorderUi();

        if (!ok || f == null || !f.exists() || f.length() < 900L || dur < 400L) {
            if (f != null && f.exists()) f.delete();
            toast("Too short — nothing saved");
            return;
        }
        loadFiles();
        toast("Saved · " + fmt(dur));
    }

    private void discardTake() {
        if (recorder == null) return;
        try {
            recorder.stop();
        } catch (Exception ignored) {
        }
        releaseRecorder();
        File f = currentFile;
        currentFile = null;
        resetRecorderUi();
        if (f != null && f.exists()) f.delete();
        toast("Take discarded");
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try {
                recorder.reset();
            } catch (Exception ignored) {
            }
            try {
                recorder.release();
            } catch (Exception ignored) {
            }
            recorder = null;
        }
    }

    private void resetRecorderUi() {
        stopTicker();
        isPaused = false;
        elapsed = 0L;
        recBtn.setBackgroundResource(R.drawable.bg_rec_idle);
        recInner.setBackgroundResource(R.drawable.bg_inner_idle);
        pauseBtn.setEnabled(false);
        pauseBtn.setText("\u23F8");
        discardBtn.setEnabled(false);
        status.setText("READY");
        status.setTextColor(C_MUTED);
        timer.setText("00:00");
        wave.setLive(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ------------------------------------------------------------------
    // playback
    // ------------------------------------------------------------------

    private void togglePlay(File f, View row) {
        Button play = (Button) row.findViewById(R.id.rowPlay);

        if (player != null && f.equals(playingFile)) {
            if (player.isPlaying()) {
                player.pause();
                play.setText("\u25B6");
            } else {
                player.start();
                play.setText("\u23F8");
            }
            return;
        }

        stopPlayback();

        player = new MediaPlayer();
        playingFile = f;
        playingRow = row;

        try {
            player.setDataSource(f.getAbsolutePath());
            player.prepare();
            player.start();
            play.setText("\u23F8");

            final ProgressBar prog = (ProgressBar) row.findViewById(R.id.rowProgress);
            prog.setMax(1000);
            final MediaPlayer p = player;

            ui.post(new Runnable() {
                @Override
                public void run() {
                    if (player != p || player == null) return;
                    try {
                        int dur = player.getDuration();
                        if (dur > 0) {
                            prog.setProgress((int) (player.getCurrentPosition() * 1000L / dur));
                        }
                    } catch (Exception ignored) {
                    }
                    ui.postDelayed(this, 150);
                }
            });

            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopPlayback();
                }
            });
        } catch (Exception e) {
            toast("Could not play this clip");
            stopPlayback();
        }
    }

    private void stopPlayback() {
        if (playingRow != null) {
            Button b = (Button) playingRow.findViewById(R.id.rowPlay);
            ProgressBar p = (ProgressBar) playingRow.findViewById(R.id.rowProgress);
            if (b != null) b.setText("\u25B6");
            if (p != null) p.setProgress(0);
        }
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        playingFile = null;
        playingRow = null;
    }

    // ------------------------------------------------------------------
    // export / delete
    // ------------------------------------------------------------------

    private void export(File f) {
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingExport = f;
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Audio.Media.DISPLAY_NAME, f.getName());
                v.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
                v.put(MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/Voice Recorder");
                Uri uri = getContentResolver()
                        .insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IOException("insert failed");
                OutputStream os = getContentResolver().openOutputStream(uri);
                copyFile(f, os);
                toast("Exported to Music/Voice Recorder");
            } else {
                File dest = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_MUSIC),
                        "Voice Recorder");
                if (!dest.exists()) dest.mkdirs();
                copyFile(f, new FileOutputStream(new File(dest, f.getName())));
                toast("Exported to Music/Voice Recorder");
            }
        } catch (Exception e) {
            toast("Export failed (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void copyFile(File src, OutputStream os) throws IOException {
        InputStream is = new FileInputStream(src);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            os.flush();
        } finally {
            try {
                is.close();
            } catch (Exception ignored) {
            }
            try {
                os.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void confirmDelete(final File f) {
        new AlertDialog.Builder(this)
                .setTitle("Delete recording?")
                .setMessage(titleFor(f) + "\n\nThis can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (f.equals(playingFile)) stopPlayback();
                        if (f.exists()) f.delete();
                        loadFiles();
                        toast("Deleted");
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------
    // permissions / lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;

        if (code == REQ_AUDIO) {
            if (granted) {
                startRecording();
            } else {
                toast("Microphone permission is needed to record");
            }
        } else if (code == REQ_WRITE) {
            if (granted && pendingExport != null) {
                File f = pendingExport;
                pendingExport = null;
                export(f);
            } else {
                toast("Storage permission is needed to export");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTicker();
        stopPlayback();
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Exception ignored) {
            }
            releaseRecorder();
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
