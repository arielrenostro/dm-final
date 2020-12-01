package com.furb.carmonitorfinal;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btSelectDevice).setOnClickListener(this::onClickSelectDevice);
    }

    private void onClickSelectDevice(View view) {
        boolean started = this.bluetoothAdapter.startDiscovery();
        if (!started) {
            Context context = getApplicationContext();
            Toast toast = Toast.makeText(context, "Não foi possível iniciar a descoberta dos dispositivos", 5000);
            toast.show();
        }
    }
}