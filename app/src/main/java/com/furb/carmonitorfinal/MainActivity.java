package com.furb.carmonitorfinal;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
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
import com.furb.carmonitorfinal.services.FuelNotificationService;
import com.furb.carmonitorfinal.services.OBD2Service;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
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

    private Messenger mMessenger;
    private boolean notified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Receiver
        registerReceiver(this.receiver, new IntentFilter("com.furb.carmonitorfinal.RPM"));
        registerReceiver(this.receiver, new IntentFilter("com.furb.carmonitorfinal.SPEED"));
        registerReceiver(this.receiver, new IntentFilter("com.furb.carmonitorfinal.FUEL_LEVEL"));

        // BindServices
        bindMessageService();

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
        txtRPM.setText(String.valueOf(rpm));
    }

    @Override
    public void onFuelLevel(float fuelLevel) {
        String fuelLevelText = fuelLevel + "%";

        TextView txtFuelLevel = findViewById(R.id.txtFuelLevel);
        txtFuelLevel.setText(fuelLevelText);

        if (!this.notified && MIN_FUEL_ALERT > fuelLevel) {
            this.notified = true;

            Message message = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("message", "Seu tanque está ficando vazio! Deseja procurar o posto mais próximo?");
            bundle.putString("participant", "Tanque abaixo de " + fuelLevelText + "%");
            message.setData(bundle);
            try {
                this.mMessenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onSpeed(int speed) {
        TextView txtSpeed = findViewById(R.id.txtSpeed);
        txtSpeed.setText(speed + "km/h");
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
        BluetoothSocket bluetoothSocket = null;
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            Toast.makeText(this, "Conectado: " + deviceName, Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Class<?> clazz = bluetoothSocket.getRemoteDevice().getClass();
            Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
            try {
                Method m = clazz.getMethod("createRfcommSocket", paramTypes);
                Object[] params = new Object[]{1};

                bluetoothSocket = (BluetoothSocket) m.invoke(bluetoothDevice, params);
                bluetoothSocket.connect();

                Toast.makeText(this, "Conectado: " + deviceName, Toast.LENGTH_LONG).show();

            } catch (Exception e2) {
                Toast.makeText(this, "ERRO AO CONECTAR: " + e.getMessage(), Toast.LENGTH_LONG).show();
                bluetoothSocket = null;
            }
        } finally {
            if (bluetoothSocket != null) {

                final BluetoothSocket socket = bluetoothSocket;
                try {
                    IConnectionHandler connectionHandler = (context, name, service) -> {
                        try {
                            OBD2Service.MyBinder binder = (OBD2Service.MyBinder) service;
                            context.obd2Service = binder.getService();
                            context.obd2Service.register(socket, bluetoothDevice);
                        } catch (Exception e) {
                            Toast.makeText(this, "ERRO AO CONECTAR: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }

                        EditText txtDevice = findViewById(R.id.txtDevice);
                        txtDevice.setText(deviceName);
                    };
                    bindService(new Intent(this, OBD2Service.class), new Connection(this, connectionHandler), BIND_AUTO_CREATE);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "ERRO AO CONECTAR: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
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

    private void bindMessageService() {
        IConnectionHandler connectionHandler = (context, name, service) -> context.mMessenger = new Messenger(service);
        bindService(new Intent(this, FuelNotificationService.class), new Connection(this, connectionHandler), BIND_AUTO_CREATE);
    }

    private void showLoader() {
        this.loaderDialog = new Dialog(this);
        this.loaderDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.loaderDialog.setCancelable(false);

        ProgressBar progress = new ProgressBar(this);
        this.loaderDialog.addContentView(progress, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.loaderDialog.show();
    }

    private void hideLoader() {
        this.loaderDialog.dismiss();
    }

    private class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if ("com.furb.carmonitorfinal.RPM".equals(action)) {
                int rpm = intent.getIntExtra("rpm", 0);
                onRPM(rpm);

            } else if ("com.furb.carmonitorfinal.SPEED".equals(action)) {
                int speed = intent.getIntExtra("speed", 0);
                onSpeed(speed);

            } else if ("com.furb.carmonitorfinal.FUEL_LEVEL".equals(action)) {
                float fuelLevel = intent.getFloatExtra("level", 0.0f);
                onFuelLevel(fuelLevel);
            }
        }
    }

    public static class Connection implements ServiceConnection {

        IConnectionHandler connectionHandler;
        WeakReference<Context> contextReference;

        public Connection(Context context, IConnectionHandler connectionHandler) {
            this.contextReference = new WeakReference<>(context);
            this.connectionHandler = connectionHandler;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Context context = this.contextReference.get();
            if (context != null) {
                MainActivity mainActivity = (MainActivity) context;
                this.connectionHandler.onConnected(mainActivity, name, service);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Context context = contextReference.get();
            if (context != null) {
                MainActivity mainActivity = (MainActivity) context;
                this.connectionHandler.onDisconnected(mainActivity, name);
            }
        }
    }

    private interface IConnectionHandler {

        void onConnected(MainActivity context, ComponentName name, IBinder service);

        default void onDisconnected(MainActivity context, ComponentName name) {

        }
    }
}