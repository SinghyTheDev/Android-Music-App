package com.example.mp3player;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteCallbackList;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class MP3Service extends Service {

    private final String CHANNEL_ID = "100";
    private final int NOTIFICATION_ID = 1;
    private MP3Player player;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("MP3Service", "onCreate");
        player = new MP3Player();
    }

    private void createNotification() {
        // Initialise notification manager and its properties
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "MP3Player";
            String description = "Channel";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }

        // Initialise notification and its properties
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent
                .getActivity(this, 0, intent, 0);
        NotificationCompat.Builder mBuilder = new NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle("Playing track")
                .setContentText("Tap to open MP3Player")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        startForeground(NOTIFICATION_ID, mBuilder.build());
    }

    RemoteCallbackList<MyBinder> remoteCallbackList = new RemoteCallbackList<>();
    private void doCallbacks() {
        Log.d("MP3Service", "doCallbacks");
        String filePath = player.getFilePath();
        int duration = player.getDuration();
        new Thread(() -> {
            try {
                // Do callbacks while the player is playing and the song hasn't changed, if user
                // changes song then the initial songs filePath becomes different to new songs
                // filePath and then loop is broken, this prevents multiple threads being created
                while (player.getState() != MP3Player.MP3PlayerState.STOPPED
                        && filePath == player.getFilePath()) {
                    Log.d("MP3Service", "Song progress: " + player.getProgress() / 1000);

                    final int n = remoteCallbackList.beginBroadcast();
                    for (int i = 0; i < n; i++) {
                        remoteCallbackList.getBroadcastItem(i).callback
                                .durationEvent(player.getDuration());
                        remoteCallbackList
                                .getBroadcastItem(i)
                                .callback
                                .progressEvent(player.getProgress());
                    }
                    remoteCallbackList.finishBroadcast();

                    // Thread sleeps for 1 second in each loop to avoid unnecessarily updating
                    // song progress to the same second
                    Thread.sleep(1000);

                    // If song reaches the end where its progress would become equal to its
                    // duration, stop the song. This then breaks the outer loop as it
                    // requires the song to not be stopped, and therefore resources are not
                    // unnecessarily wasted
                    if (player.getProgress() == duration) {
                        Log.d("MP3Service", "Song finished");
                        player.stop();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d("MP3Service", "onBind");
        return new MyBinder();
    }

    @Override
    public void onDestroy() {
        player.stop();
        Log.d("MP3Service", "onDestroy");
        super.onDestroy();
    }

    public class MyBinder extends Binder implements IInterface {
        ICallback callback;

        void load(String filePath) {
            // If a song was already loaded before the user selected another song, then
            // stop this song first
            if (player.getState() == MP3Player.MP3PlayerState.PLAYING ||
                    player.getState() == MP3Player.MP3PlayerState.PAUSED) {
                player.stop();
            } else {
                // If a song was not loaded before the user selected another song, this means
                // there was no notification displaying, therefore we create the notification
                createNotification();
            }

            // Load the song and start doing callbacks
            player.load(filePath);
            doCallbacks();
        }

        void play() {
            if (player.getState() == MP3Player.MP3PlayerState.PAUSED) {
                Log.d("MP3Service", "Playing");
                player.play();
            }
        }

        void pause() {
            if (player.getState() == MP3Player.MP3PlayerState.PLAYING) {
                Log.d("MP3Service", "Pausing");
                player.pause();
            }
        }

        void stop() {
            if (player.getState() != MP3Player.MP3PlayerState.STOPPED) {
                Log.d("MP3Service", "Stopping");

                // Get rid of notification when user stops playing song
                NotificationManager notificationManager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                notificationManager.cancel(NOTIFICATION_ID);
                stopForeground(true);
                player.stop();
            }
        }

        void registerCallback(ICallback callback) {
            this.callback = callback;
            remoteCallbackList.register(MyBinder.this);
        }

        void unRegisterCallBack() {
            remoteCallbackList.unregister(MyBinder.this);
        }

        MP3Player.MP3PlayerState getState() {
            return player.getState();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}