/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.furb.carmonitorfinal.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.furb.carmonitorfinal.R;

public class FuelNotificationService extends Service {

    public static final String READ_ACTION = "com.furb.carmonitorfinal.ACTION_MESSAGE_READ";
    public static final String REPLY_ACTION = "com.furb.carmonitorfinal.ACTION_MESSAGE_REPLY";
    public static final String CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";

    private static final String CHANNEL_ID = "canalbolado";

    private static final String TAG = FuelNotificationService.class.getSimpleName();

    private final Messenger mMessenger = new Messenger(new IncomingHandler());

    private NotificationManagerCompat mNotificationManager;

    @Override
    public void onCreate() {
        mNotificationManager = NotificationManagerCompat.from(getApplicationContext());
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(getString(R.string.channel_description));

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void sendNotification(int conversationId, String message, String participant, long timestamp) {
        PendingIntent markAsReadPendingIntent = PendingIntent.getBroadcast(getApplicationContext(),
                conversationId,
                createIntent(conversationId, FuelNotificationService.READ_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action markAsReadAction = new NotificationCompat.Action.Builder(R.drawable.ic_launcher_foreground, "Marcar como lido", markAsReadPendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build();

        PendingIntent replyIntent = PendingIntent.getBroadcast(getApplicationContext(),
                conversationId,
                createIntent(conversationId, FuelNotificationService.REPLY_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Action action = new NotificationCompat.Action.Builder(R.drawable.ic_launcher_foreground, "Responder", replyIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .addRemoteInput(new RemoteInput.Builder("reply_input").build())
                .build();

        Bitmap icon = getBitmapFromVectorDrawable(getApplicationContext(), R.drawable.ic_launcher_foreground);

        Person person = new Person.Builder()
                .setName(getString(R.string.app_name))
                .setIcon(IconCompat.createWithBitmap(icon))
                .build();

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(person);
        messagingStyle.setConversationTitle(participant);
        messagingStyle.setGroupConversation(false);

        messagingStyle.addMessage(message, timestamp, person);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                // Set the application notification icon:
                .setSmallIcon(R.drawable.ic_launcher_foreground)

                // Set the large icon, for example a picture of the other recipient of the message
                .setLargeIcon(icon)

                .setStyle(messagingStyle)

                .addAction(action)
                .addInvisibleAction(markAsReadAction);

        mNotificationManager.notify(conversationId, builder.build());
    }

    public static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private Intent createIntent(int conversationId, String action) {
        return new Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(action)
                .putExtra(FuelNotificationService.CONVERSATION_ID, conversationId);
    }

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            if (bundle.get("message") != null && bundle.get("participant") != null) {
                sendNotification(1, bundle.getString("message"), bundle.getString("participant"), System.currentTimeMillis());
            }
        }
    }
}
