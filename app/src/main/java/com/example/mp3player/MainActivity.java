package com.example.mp3player;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.database.Cursor;
import android.provider.MediaStore;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private MP3Service.MyBinder mp3Service = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "onCreate");
        setContentView(R.layout.activity_main);

        // Create new intent between this activity and MP3Service, then use the intent to
        // bind this activity to Mp3Service, and then start the service
        Intent intent = new Intent(this, MP3Service.class);
        this.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d("MainActivity", "bindService");
        this.startService(intent);

        // Set up the ListView for displaying tracks
        final ListView lv = findViewById(R.id.trackListView);
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Media.IS_MUSIC + "!= 0",
                null,
                null);
        lv.setAdapter(new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1,
                cursor,
                new String[]{MediaStore.Audio.Media.DATA},
                new int[]{android.R.id.text1}));
        lv.setOnItemClickListener((myAdapter, myView, myItemInt, mylng) -> {
            Cursor c = (Cursor) lv.getItemAtPosition(myItemInt);
            @SuppressLint("Range") String uri =
                    c.getString(c.getColumnIndex(MediaStore.Audio.Media.DATA));

            // Load the song which the user has selected
            Log.d("MainActivity", "Song selected: " + uri);
            mp3Service.load(uri);
        });
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        //Bind service and register callbacks
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("MainActivity", "onServiceConnected");
            mp3Service = (MP3Service.MyBinder) service;
            mp3Service.registerCallback(callback);
        }
        //Unbind service and unregister callbacks
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("MainActivity", "onServiceDisconnected");
            mp3Service.unRegisterCallBack();
            mp3Service = null;
        }
    };

    // Play the song when user clicks on play button
    public void onPlayClick(View v) {
        mp3Service.play();
    }

    // Pause the song when user clicks on pause button
    public void onPauseClick(View v) {
        mp3Service.pause();
    }

    // Stop the song when user clicks on stop button, then reset UI elements
    @SuppressLint("SetTextI18n")
    public void onStopClick(View v) {
        mp3Service.stop();
        ((TextView) findViewById(R.id.progressTextView)).setText("0:00");
        ((TextView) findViewById(R.id.durationTextView)).setText("0:00");
        ((SeekBar) findViewById(R.id.progressSeekBar)).setProgress(0);
    }

    ICallback callback = new ICallback() {
        // Callback function which sets durationTextView and progressSeekBar max
        // to songs duration
        @Override
        public void durationEvent(int duration) {
            runOnUiThread(() -> {
                ((TextView) findViewById(R.id.durationTextView)).setText(timeToString(duration));
                ((SeekBar) findViewById(R.id.progressSeekBar)).setMax(duration);
            });
        }
        // Callback function which updates progressTextView and progressSeekBar to
        // songs progress during playback
        public void progressEvent(int progress) {
            runOnUiThread(() -> {
                if (mp3Service.getState() == MP3Player.MP3PlayerState.PLAYING ||
                        mp3Service.getState() == MP3Player.MP3PlayerState.PAUSED) {
                    ((TextView) findViewById(R.id.progressTextView)).setText(timeToString(progress));
                    ((SeekBar) findViewById(R.id.progressSeekBar)).setProgress(progress);
                }
            });
        }
    };

    // Function to convert milliseconds to string, for instance 1000 milliseconds would be
    // converted to string "00:01"
    private String timeToString(int milliseconds) {
        long minutes = (milliseconds / 1000) / 60 % 60;
        long seconds = (milliseconds / 1000) % 60;
        // If seconds are less than 10 then insert "0" in front, for instance 8 seconds
        // would become "00:08" instead of "00:8"
        if (seconds < 10) {
            return (minutes + ":0" + seconds);
        } else {
            return (minutes + ":" + seconds);
        }
    }

    // When onDestroy is called, unbind service and make connection to service null
    // to free unneeded resources
    @Override
    protected void onDestroy() {
        Log.d("MainActivity", "onDestroy");
        if (serviceConnection != null) {
            unbindService(serviceConnection);
            serviceConnection = null;
            Log.d("MainActivity", "unbindService");
        }
        super.onDestroy();
    }
}