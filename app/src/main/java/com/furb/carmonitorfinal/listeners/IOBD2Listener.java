package com.furb.carmonitorfinal.listeners;

public interface IOBD2Listener {

    void onRPM(int rpm);

    void onFuelLevel(float fuelLevel);

    void onSpeed(int speed);
}
