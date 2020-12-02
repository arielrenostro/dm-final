package com.furb.carmonitorfinal;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.furb.carmonitorfinal.listeners.IOBD2Listener;
import com.furb.carmonitorfinal.services.OBD2Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MainActivity extends AppCompatActivity implements IOBD2Listener {

    private final static int REQUEST_ENABLE_BT = 1;
    private final static UUID MY_UUID = UUID.randomUUID();

    private final static float MIN_FUEL_ALERT = 15.0f;

    private final BroadcastReceiver receiver = new Receiver();
    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private OBD2Service obd2Service;
    private Dialog loaderDialog;

    private boolean notified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        // Receiver
//        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
//        registerReceiver(this.receiver, filter);

        // Events
        findViewById(R.id.btSelectDevice).setOnClickListener(this::onClickSelectDevice);

        // Bluetooth
        Context context = getApplicationContext();
        if (this.bluetoothAdapter != null) {
            if (!this.bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {
            Toast.makeText(context, "O dispositivo não possui bluetooth", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.receiver);
    }

    private void onClickSelectDevice(View view) {
        Context context = getApplicationContext();
        try {
            if (this.bluetoothAdapter.isDiscovering()) {
                Toast.makeText(context, "Já está procurando dispositivos. Aguarde!", Toast.LENGTH_LONG).show();
                return;
            }

            Set<BluetoothDevice> bondedDevices = this.bluetoothAdapter.getBondedDevices();
            openDialog(bondedDevices);
        } catch (SecurityException e) {
            Toast.makeText(context, "Erro de permissão: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRPM(int rpm) {
        TextView txtRPM = findViewById(R.id.txtRPM);
        txtRPM.setText(rpm);
    }

    @Override
    public void onFuelLevel(float fuelLevel) {
        TextView txtFuelLevel = findViewById(R.id.txtFuelLevel);
        txtFuelLevel.setText(fuelLevel + "%");

        if (!this.notified && MIN_FUEL_ALERT > fuelLevel) {
            // TODO ARIEL
        }
    }

    private void openDialog(Set<BluetoothDevice> bondedDevices) {
        Map<Integer, BluetoothDevice> devicesByIdx = getDevicesByIdx(bondedDevices);
        String[] deviceNames = devicesByIdx.keySet()//
                .stream()//
                .sorted()//
                .map(idx -> {
                    BluetoothDevice deviceByIdx = devicesByIdx.get(idx);
                    return deviceByIdx.getName() + " - " + deviceByIdx.getAddress();
                })//
                .toArray(String[]::new);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Escolha um dispositivo para conectar");
        builder.setItems(deviceNames, (dialog, which) -> {
            BluetoothDevice selectedDevice = devicesByIdx.get(which);
            if (selectedDevice == null) {
                Toast.makeText(this, "Dispositivo não encontrado. Tente novamente!", Toast.LENGTH_LONG).show();
                return;
            }
            String deviceName = deviceNames[which];
            connectToDevice(deviceName, selectedDevice);
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void connectToDevice(String deviceName, BluetoothDevice bluetoothDevice) {
        showLoader();
        try {
            BluetoothSocket bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            Toast.makeText(this, "Conectado: " + deviceName, Toast.LENGTH_LONG).show();

            EditText txtDevice = findViewById(R.id.txtDevice);
            txtDevice.setText(deviceName);

            this.obd2Service = new OBD2Service(bluetoothSocket, bluetoothDevice, this);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "ERRO AO CONECTAR: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            hideLoader();
        }
    }

    private Map<Integer, BluetoothDevice> getDevicesByIdx(Set<BluetoothDevice> bondedDevices) {
        List<BluetoothDevice> deviceList = new ArrayList<>(bondedDevices);

        Map<Integer, BluetoothDevice> map = new HashMap<>();
        for (int i = 0; i < deviceList.size(); i++) {
            map.put(i, deviceList.get(i));
        }
        return map;
    }

    public void showLoader() {
        this.loaderDialog = new Dialog(this);
        this.loaderDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.loaderDialog.setCancelable(false);

        ProgressBar progress = new ProgressBar(this);
        this.loaderDialog.addContentView(progress, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.loaderDialog.show();
    }

    public void hideLoader() {
        this.loaderDialog.dismiss();
    }

    private class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
        }
    }
}