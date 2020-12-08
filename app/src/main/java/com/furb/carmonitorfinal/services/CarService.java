package com.furb.carmonitorfinal.services;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.furb.carmonitorfinal.screens.NavigationScreen;
import com.google.android.libraries.car.app.AppManager;
import com.google.android.libraries.car.app.CarAppService;
import com.google.android.libraries.car.app.Screen;
import com.google.android.libraries.car.app.ScreenManager;
import com.google.android.libraries.car.app.SurfaceListener;

public class CarService extends CarAppService {

    SurfaceListener surfaceListener;
    ScreenManager screenManager;

    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull Intent intent) {
        this.getCarContext().getCarService(AppManager.class).setSurfaceListener(surfaceListener);

        return new NavigationScreen(getCarContext());
    }


    @Override
    public void onNewIntent(@NonNull Intent intent) {
        Screen top = screenManager.getTop();
        if (!(top instanceof NavigationScreen)) {
            ScreenManager screenManager = getCarContext().getCarService(ScreenManager.class);
            screenManager.push(new NavigationScreen(getCarContext()));
        }
    }
}
