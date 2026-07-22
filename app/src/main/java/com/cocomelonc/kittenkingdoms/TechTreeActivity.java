/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * A standalone research screen, reached from the world view's Research button and returned from
 * via the system Back gesture/button like any other Activity. State travels in as Intent extras
 * (tech point pool, active selection, packed unlocked bits, language) and the chosen technology
 * travels back out the same way, applied by {@code MainActivity.onActivityResult}.
 */
public final class TechTreeActivity extends Activity implements TechTreeView.Listener {
    static final int REQUEST_CODE = 1001;
    static final String EXTRA_TECH_POINTS = "tech_points";
    static final String EXTRA_ACTIVE_TECH = "active_tech";
    static final String EXTRA_UNLOCKED_BITS = "unlocked_bits";
    static final String EXTRA_LANGUAGE = "language";
    static final String EXTRA_TURN = "turn";
    static final String EXTRA_POPULATION = "population";
    static final String EXTRA_RESOURCES = "resources";
    static final String EXTRA_BUILDING_COUNTS = "building_counts";
    static final String EXTRA_SELECTED_TECH = "selected_tech";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = getIntent();
        int techPoints = intent.getIntExtra(EXTRA_TECH_POINTS, 0);
        int activeTech = intent.getIntExtra(EXTRA_ACTIVE_TECH, TechNode.NONE);
        int unlockedBits = intent.getIntExtra(EXTRA_UNLOCKED_BITS, 0);
        String language = intent.getStringExtra(EXTRA_LANGUAGE);
        if (language == null) {
            language = "en";
        }

        techTreeView = new TechTreeView(this, techPoints, activeTech, unlockedBits,
                intent.getIntExtra(EXTRA_TURN, 0), intent.getIntExtra(EXTRA_POPULATION, 0),
                intent.getIntArrayExtra(EXTRA_RESOURCES),
                intent.getIntArrayExtra(EXTRA_BUILDING_COUNTS), language, this);
        setContentView(techTreeView);
        enterImmersiveMode();
    }

    private TechTreeView techTreeView;

    @Override
    protected void onDestroy() {
        if (techTreeView != null) {
            techTreeView.close();
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    @Override
    public void onTechSelected(int techId) {
        Intent result = new Intent();
        result.putExtra(EXTRA_SELECTED_TECH, techId);
        setResult(RESULT_OK, result);
    }

    private void enterImmersiveMode() {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
    }
}
