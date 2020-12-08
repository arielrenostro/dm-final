package com.furb.carmonitorfinal.screens;

import androidx.annotation.NonNull;

import com.google.android.libraries.car.app.CarContext;
import com.google.android.libraries.car.app.Screen;
import com.google.android.libraries.car.app.model.Action;
import com.google.android.libraries.car.app.model.ActionStrip;
import com.google.android.libraries.car.app.model.CarColor;
import com.google.android.libraries.car.app.model.Template;
import com.google.android.libraries.car.app.navigation.model.NavigationTemplate;

public class NavigationScreen extends Screen {

    public NavigationScreen(@NonNull CarContext carContext) {
        super(carContext);
    }

    @NonNull
    @Override
    public Template getTemplate() {
        return NavigationTemplate
                .builder()
                .setActionStrip(
                        ActionStrip
                                .builder()
                                .addAction(Action.BACK)
                                .build()
                )
                .setBackgroundColor(CarColor.BLUE)
                .build();
    }
}
