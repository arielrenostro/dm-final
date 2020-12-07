package com.furb.carmonitorfinal.services;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OBD2Service extends Service {

    private static final byte[] CMD_NEW_LINE = "\r".getBytes();

    private static final byte[] CMD_RESET = "AT Z".getBytes();
    private static final long CMD_RESET_DELAY = 500;

    private static final byte[] CMD_RPM = "01 0C".getBytes();
    private static final long CMD_RPM_DELAY = 500;

    private static final byte[] CMD_FUEL_LEVEL = "01 2F".getBytes();
    private static final long CMD_FUEL_LEVEL_DELAY = 500;

    private static final byte[] CMD_SPEED = "01 0D".getBytes();
    private static final long CMD_SPEED_DELAY = 500;

    private static final long LOOP_DELAY = 1000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final IBinder mBinder = new MyBinder();

    private BluetoothSocket socket;
    private BluetoothDevice device;

    private InputStream is;
    private OutputStream os;

    public void register(BluetoothSocket socket, BluetoothDevice device) throws IOException {
        this.socket = socket;
        this.device = device;

        this.is = this.socket.getInputStream();
        this.os = this.socket.getOutputStream();

        this.start();
        this.executor.submit(this::run);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    private void start() throws IOException {
        send(CMD_RESET, CMD_RESET_DELAY);
    }

    public void run() {
        long start;

        while (true) {
            start = System.currentTimeMillis();
            try {
                // ignore first two bytes [41 0C] of the response((A*256)+B)/4
                byte[] rpm = send(CMD_RPM, CMD_RPM_DELAY);
                Intent rpmIntent = new Intent("com.furb.carmonitorfinal.RPM");
                rpmIntent.putExtra("rpm", (rpm[2] * 256 + rpm[3]) / 4);
                sendBroadcast(rpmIntent);

                // ignore first two bytes [hh hh] of the response
                byte[] fuelLevel = send(CMD_FUEL_LEVEL, CMD_FUEL_LEVEL_DELAY);
                Intent fuelLevelIntent = new Intent("com.furb.carmonitorfinal.FUEL_LEVEL");
                fuelLevelIntent.putExtra("level", 100.0f * fuelLevel[2] / 255.0f);
                sendBroadcast(fuelLevelIntent);

                byte[] speed = send(CMD_SPEED, CMD_SPEED_DELAY);
                Intent speedIntent = new Intent("com.furb.carmonitorfinal.SPEED");
                speedIntent.putExtra("speed", (int) speed[2]);
                sendBroadcast(speedIntent);

                delay(LOOP_DELAY - (System.currentTimeMillis() - start));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] send(byte[] cmd, long delay) throws IOException {
        this.os.write(cmd);
        this.os.write(CMD_NEW_LINE);
        this.os.flush();

        delay(delay);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(this.is.available());
        byte b;
        char c;
        while (((b = (byte) this.is.read()) > -1)) {
            c = (char) b;
            if (c == '>') {
                break;
            }
            baos.write(b);
        }
        return baos.toByteArray();
    }

    private void delay(long delay) {
        if (delay <= 0) {
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (Exception ignored) {
        }
    }

    public BluetoothSocket getSocket() {
        return socket;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public class MyBinder extends Binder {

        public OBD2Service getService() {
            return OBD2Service.this;
        }
    }
}
