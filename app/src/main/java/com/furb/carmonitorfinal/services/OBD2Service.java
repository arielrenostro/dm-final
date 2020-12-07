package com.furb.carmonitorfinal.services;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.widget.Toast;

import com.furb.carmonitorfinal.listeners.IOBD2Listener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class OBD2Service implements Runnable {

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

    private final BluetoothSocket socket;
    private final BluetoothDevice device;
    private final IOBD2Listener listener;

    private final InputStream is;
    private final OutputStream os;

    public OBD2Service(BluetoothSocket socket, BluetoothDevice device, IOBD2Listener listener) throws IOException {
        this.socket = socket;
        this.device = device;
        this.listener = listener;

        this.is = socket.getInputStream();
        this.os = socket.getOutputStream();

        this.start();
    }

    private void start() throws IOException {
        byte[] resetResponse = send(CMD_RESET, CMD_RESET_DELAY);
        Toast.makeText((Context) this.listener, "Reset: " + new String(resetResponse), Toast.LENGTH_LONG).show();
    }

    @Override
    public void run() {
        long start;

        while (true) {
            start = System.currentTimeMillis();
            try {
                byte[] rpm = send(CMD_RPM, CMD_RPM_DELAY);
                // ignore first two bytes [41 0C] of the response((A*256)+B)/4
                this.listener.onRPM((rpm[2] * 256 + rpm[3]) / 4);

                byte[] fuelLevel = send(CMD_FUEL_LEVEL, CMD_FUEL_LEVEL_DELAY);
                // ignore first two bytes [hh hh] of the response
                this.listener.onFuelLevel(100.0f * fuelLevel[2] / 255.0f);

                byte[] speed = send(CMD_SPEED, CMD_SPEED_DELAY);
                this.listener.onSpeed(speed[2]);

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
}
