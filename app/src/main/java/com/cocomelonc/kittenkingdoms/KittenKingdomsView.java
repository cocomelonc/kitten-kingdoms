/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Dependency-free renderer and input layer: a pannable/zoomable world viewport plus a fixed
 * logical 1280x720 HUD, following the family's single-View-does-everything convention.
 */
final class KittenKingdomsView extends View implements KingdomWorld.Listener {
    private enum Overlay {
        NONE,
        BUILD_MENU
    }

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float TILE = 64f;
    private static final float MAP_LEFT = 20f;
    private static final float MAP_TOP = 90f;
    private static final float MAP_RIGHT = 1260f;
    private static final float MAP_BOTTOM = 648f;
    private static final float MIN_ZOOM = 0.6f;
    private static final float MAX_ZOOM = 1.8f;
    private static final float DRAG_THRESHOLD = 14f;
    private static final float PAUSE_X = 1218f;
    private static final float PAUSE_Y = 53f;
    private static final float BUILD_BTN_CX = 380f;
    private static final float RESEARCH_BTN_CX = 640f;
    private static final float END_TURN_BTN_CX = 900f;
    private static final float BOTTOM_BTN_CY = 686f;
    private static final float BOTTOM_BTN_HALF_W = 108f;
    private static final float BOTTOM_BTN_HALF_H = 27f;
    private static final float MODAL_LEFT = 90f;
    private static final float MODAL_TOP = 70f;
    private static final float MODAL_RIGHT = 1190f;
    private static final float MODAL_BOTTOM = 670f;
    private static final float MENU_BTN_CX = 660f;
    private static final float MENU_BTN_HALF_W = 190f;
    private static final float MENU_BTN_HEIGHT = 54f;
    private static final float MENU_BTN_GAP = 14f;
    private static final float MENU_TOP = 226f;
    private static final String PREFS = "kitten_kingdoms_progress";
    private static final String PREF_LANGUAGE = "language";
    private static final String SAVE_FILE_NAME = "kingdom.sav";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final SharedPreferences preferences;
    private final AudioEngine audio = new AudioEngine();
    private final MusicEngine music;
    private final KingdomWorld world;
    private final TerrainType[] terrainTypes = TerrainType.createAll();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random(0xA17E5501L);
    private final File saveFile;
    private final ScaleGestureDetector scaleGestureDetector;

    private Context localizedContext;
    private String language;
    private LinearGradient outsideGradient;
    private boolean hasSaveFile;
    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;
    private long lastFrameNanos;
    private boolean hostResumed = true;
    private float cameraX;
    private float cameraY;
    private float zoom = 1f;
    private float downX;
    private float downY;
    private float downCameraX;
    private float downCameraY;
    private boolean isDragging;
    private boolean multiTouchOccurred;
    private Overlay activeOverlay = Overlay.NONE;
    private KingdomWorld.State lastVisualState = KingdomWorld.State.TITLE;
    private float overlayProgress = 1f;
    private int completedBuildingsCount;
    private boolean confirmingNewKingdom;

    KittenKingdomsView(Context context) {
        super(context);
        music = new MusicEngine(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setKeepScreenOn(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        language = preferences.getString(PREF_LANGUAGE, "en");
        if (!"ru".equals(language)) {
            language = "en";
        }
        applyLanguage(language);
        android.graphics.Typeface font = context.getResources().getFont(R.font.nunito);
        regular = android.graphics.Typeface.create(font, android.graphics.Typeface.NORMAL);
        bold = android.graphics.Typeface.create(font, android.graphics.Typeface.BOLD);
        setContentDescription(text(R.string.accessibility_game));

        saveFile = new File(context.getFilesDir(), SAVE_FILE_NAME);
        hasSaveFile = saveFile.exists();
        world = new KingdomWorld(this);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        outsideGradient = new LinearGradient(
                0f, 0f, 0f, WORLD_HEIGHT,
                new int[]{0xFFF5EEDC, 0xFFE3DCC6, 0xFFCBC2A6},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP
        );
        centerCameraOnStart();
    }

    private void centerCameraOnStart() {
        float viewportWorldWidth = (MAP_RIGHT - MAP_LEFT) / zoom;
        float viewportWorldHeight = (MAP_BOTTOM - MAP_TOP) / zoom;
        cameraX = WorldMap.START_COL * TILE + TILE / 2f - viewportWorldWidth / 2f;
        cameraY = WorldMap.START_ROW * TILE + TILE / 2f - viewportWorldHeight / 2f;
        clampCamera();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        viewScale = Math.min(width / WORLD_WIDTH, height / WORLD_HEIGHT);
        viewOffsetX = (width - WORLD_WIDTH * viewScale) * 0.5f;
        viewOffsetY = (height - WORLD_HEIGHT * viewScale) * 0.5f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Math.min(dt, 0.05f);
        if (hostResumed) {
            world.update(dt);
            updateParticles(dt);
        }

        KingdomWorld.State visualState = world.getState();
        music.setPlaying(hostResumed && visualState == KingdomWorld.State.PLAYING);
        if (visualState != lastVisualState) {
            lastVisualState = visualState;
            overlayProgress = visualState == KingdomWorld.State.PAUSED ? 0f : 1f;
        }
        if (visualState == KingdomWorld.State.PAUSED && hostResumed) {
            overlayProgress = Math.min(1f, overlayProgress + dt * 5.5f);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(outsideGradient);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        canvas.save();
        canvas.translate(viewOffsetX, viewOffsetY);
        canvas.scale(viewScale, viewScale);
        float time = now / 1_000_000_000f;
        if (visualState == KingdomWorld.State.TITLE) {
            drawTitle(canvas, time);
        } else {
            drawPlaying(canvas, time);
        }
        canvas.restore();

        if (hostResumed) {
            postInvalidateOnAnimation();
        }
    }

    private void drawTitle(Canvas canvas, float time) {
        drawHill(canvas, 0xFFE3DCC6, 560f, 40f, 0.012f);
        drawHill(canvas, 0xFFCBC2A6, 610f, 30f, 0.016f);

        canvas.save();
        canvas.translate(300f, 470f + (float) Math.sin(time * 2f) * 3f);
        canvas.scale(1.6f, 1.6f);
        drawKittenAtOrigin(canvas, time, 1f, false);
        canvas.restore();

        drawFittedText(canvas, text(R.string.game_title), 660f, 145f,
                66f, 820f, 0xFF4A4234, true);
        drawFittedText(canvas, text(R.string.game_subtitle), 660f, 198f,
                24f, 760f, 0xFF74694F, false);

        float menuTop = MENU_TOP;
        if (hasSaveFile) {
            drawMenuButton(canvas, menuTop, text(R.string.continue_kingdom), true, time);
            menuTop += MENU_BTN_HEIGHT + MENU_BTN_GAP;
        }
        drawMenuButton(canvas, menuTop, text(R.string.new_kingdom), !hasSaveFile, time);
        menuTop += MENU_BTN_HEIGHT + MENU_BTN_GAP;
        drawMenuButton(canvas, menuTop, text(R.string.how_to_play), false, time);
        menuTop += MENU_BTN_HEIGHT + 36f;

        drawFittedText(canvas, text(R.string.tap_to_explore), 660f, menuTop,
                19f, 700f, 0xE8635A44, false);
        drawFittedText(canvas, text(R.string.build_hint), 660f, menuTop + 26f,
                19f, 700f, 0xE8635A44, false);
        drawLanguageSwitch(canvas, 1150f, 56f);

        if (confirmingNewKingdom) {
            drawNewKingdomConfirm(canvas);
        }
    }

    private void drawMenuButton(Canvas canvas, float top, String label, boolean emphasize, float time) {
        float bottom = top + MENU_BTN_HEIGHT;
        float cy = (top + bottom) / 2f;
        if (emphasize) {
            float pulse = 0.98f + 0.02f * (float) Math.sin(time * 2.6f);
            canvas.save();
            canvas.scale(pulse, pulse, MENU_BTN_CX, cy);
            drawPill(canvas, MENU_BTN_CX - MENU_BTN_HALF_W, top, MENU_BTN_CX + MENU_BTN_HALF_W, bottom,
                    0xF7FDF8EC, 0x264A4234);
            drawFittedText(canvas, label, MENU_BTN_CX, cy + 9f, 26f, MENU_BTN_HALF_W * 1.7f, 0xFF564D3B, true);
            canvas.restore();
        } else {
            drawPill(canvas, MENU_BTN_CX - MENU_BTN_HALF_W, top, MENU_BTN_CX + MENU_BTN_HALF_W, bottom,
                    0xE8FDF8EC, 0x1E4A4234);
            drawFittedText(canvas, label, MENU_BTN_CX, cy + 8f, 23f, MENU_BTN_HALF_W * 1.7f, 0xFF66604F, true);
        }
    }

    private void drawNewKingdomConfirm(Canvas canvas) {
        drawModalScrim(canvas);
        paint.setColor(0x264A4234);
        canvas.drawRoundRect(384f, 244f, 900f, 484f, 34f, 34f, paint);
        paint.setColor(0xF8FDFAF0);
        canvas.drawRoundRect(380f, 240f, 896f, 480f, 34f, 34f, paint);
        drawFittedText(canvas, text(R.string.confirm_new_kingdom_title), 638f, 306f, 27f, 460f, 0xFF443C2E, true);
        drawFittedText(canvas, text(R.string.confirm_new_kingdom_body), 638f, 352f, 18f, 460f, 0xFF74694F, false);
        drawPill(canvas, 420f, 392f, 630f, 446f, 0xFFE9B65C, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.confirm), 525f, 426f, 19f, 190f, 0xFF443C2E, true);
        drawPill(canvas, 650f, 392f, 860f, 446f, 0xFFE9DDCB, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.cancel), 755f, 426f, 19f, 190f, 0xFF443C2E, true);
    }

    private void drawPlaying(Canvas canvas, float time) {
        drawMapViewportFrame(canvas);

        canvas.save();
        canvas.clipRect(MAP_LEFT, MAP_TOP, MAP_RIGHT, MAP_BOTTOM);
        canvas.translate(MAP_LEFT, MAP_TOP);
        canvas.scale(zoom, zoom);
        canvas.translate(-cameraX, -cameraY);
        drawWorldContents(canvas, time);
        canvas.restore();

        if (activeOverlay == Overlay.NONE) {
            drawGridOverlayIfPlacing(canvas);
        }
        drawHud(canvas, time);

        if (world.getPendingBuildingTypeId() != BuildingType.NONE) {
            drawPlacementBanner(canvas);
        }

        if (activeOverlay == Overlay.BUILD_MENU) {
            drawBuildMenu(canvas);
        }

        if (world.getState() == KingdomWorld.State.PAUSED) {
            drawPauseOverlay(canvas, time);
        }
    }

    private void drawMapViewportFrame(Canvas canvas) {
        paint.setColor(0x2A4A4234);
        canvas.drawRoundRect(MAP_LEFT - 8f + 4f, MAP_TOP - 8f + 6f, MAP_RIGHT + 8f + 4f, MAP_BOTTOM + 8f + 6f,
                22f, 22f, paint);
        paint.setColor(0xFFF7F2E4);
        canvas.drawRoundRect(MAP_LEFT - 8f, MAP_TOP - 8f, MAP_RIGHT + 8f, MAP_BOTTOM + 8f, 22f, 22f, paint);
    }

    private void drawWorldContents(Canvas canvas, float time) {
        WorldMap map = world.getMap();
        float viewportWorldWidth = (MAP_RIGHT - MAP_LEFT) / zoom;
        float viewportWorldHeight = (MAP_BOTTOM - MAP_TOP) / zoom;
        int firstCol = clampInt((int) Math.floor(cameraX / TILE) - 1, 0, WorldMap.SIZE - 1);
        int firstRow = clampInt((int) Math.floor(cameraY / TILE) - 1, 0, WorldMap.SIZE - 1);
        int lastCol = clampInt((int) Math.ceil((cameraX + viewportWorldWidth) / TILE) + 1, 0, WorldMap.SIZE - 1);
        int lastRow = clampInt((int) Math.ceil((cameraY + viewportWorldHeight) / TILE) + 1, 0, WorldMap.SIZE - 1);

        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = firstCol; col <= lastCol; col++) {
                drawTile(canvas, map, row, col);
            }
        }

        for (PlacedBuilding building : world.getBuildings()) {
            if (building.row < firstRow || building.row > lastRow
                    || building.col < firstCol || building.col > lastCol) {
                continue;
            }
            float cx = building.col * TILE + TILE / 2f;
            float cy = building.row * TILE + TILE / 2f;
            drawBuildingToken(canvas, building.typeId, cx, cy, !building.isComplete(), time);
        }

        drawParticles(canvas);

        float kittenX = world.getVisualCol() * TILE + TILE / 2f;
        float kittenY = world.getVisualRow() * TILE + TILE / 2f;
        canvas.save();
        canvas.translate(kittenX, kittenY);
        canvas.scale(0.55f, 0.55f);
        drawKittenAtOrigin(canvas, time, world.getFacing(), world.getPathLength() > 0);
        canvas.restore();
    }

    private void drawTile(Canvas canvas, WorldMap map, int row, int col) {
        float left = col * TILE;
        float top = row * TILE;
        if (!map.isExplored(row, col)) {
            paint.setColor(0xFFB7BEC9);
            canvas.drawRect(left, top, left + TILE, top + TILE, paint);
            paint.setColor(0x1FFFFFFF);
            canvas.drawCircle(left + TILE * 0.5f, top + TILE * 0.5f, TILE * 0.18f, paint);
            return;
        }
        int terrainId = map.terrainAt(row, col);
        paint.setColor(terrainTypes[terrainId].color);
        canvas.drawRect(left, top, left + TILE, top + TILE, paint);
        paint.setColor(0x10FFFFFF);
        canvas.drawRect(left, top, left + TILE, top + 3f, paint);

        int seedValue = (int) (WorldMap.TERRAIN_SEED % 9973) + row * 131 + col * 17;
        if (terrainId == TerrainType.GRASS && Math.floorMod(seedValue, 7) == 0) {
            paint.setColor(0x30355A2E);
            float dotX = left + 16f + Math.floorMod(seedValue, 32);
            float dotY = top + 16f + Math.floorMod(seedValue * 3, 32);
            canvas.drawCircle(dotX, dotY, 2.4f, paint);
        }
    }

    private void drawBuildingToken(Canvas canvas, int typeId, float cx, float cy, boolean underConstruction, float time) {
        BuildingType type = world.getBuildingTypes()[typeId];
        int accent = buildingAccentColor(typeId);
        paint.setColor(0x2A4A4234);
        canvas.drawRoundRect(cx - 24f, cy - 18f, cx + 26f, cy + 27f, 8f, 8f, paint);
        paint.setColor(underConstruction ? lighten(accent, 0.4f) : accent);
        canvas.drawRoundRect(cx - 26f, cy - 24f, cx + 26f, cy + 24f, 8f, 8f, paint);
        paint.setColor(0x40FFFFFF);
        canvas.drawRoundRect(cx - 21f, cy - 19f, cx + 21f, cy - 8f, 4f, 4f, paint);
        drawBuildingGlyph(canvas, typeId, cx, cy);
        if (underConstruction) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.4f);
            paint.setColor(0xCCFFFFFF);
            float sweep = ((float) Math.sin(time * 3f) * 0.5f + 0.5f) * 300f + 30f;
            canvas.drawArc(cx - 22f, cy - 22f, cx + 22f, cy + 22f, -90f, sweep, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (!type.playerBuildable && world.isTechUnlocked(TechNode.KINGDOM_CHARTER)) {
            paint.setColor(0xFFE9B65C);
            path.reset();
            path.moveTo(cx - 10f, cy - 28f);
            path.lineTo(cx, cy - 40f);
            path.lineTo(cx + 10f, cy - 28f);
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    private int buildingAccentColor(int typeId) {
        switch (typeId) {
            case BuildingType.TOWN_HALL:
                return 0xFFC98A3D;
            case BuildingType.FISHING_DOCK:
                return 0xFF6FB0D8;
            case BuildingType.LUMBER_CAMP:
                return 0xFF7C9E5C;
            case BuildingType.QUARRY:
                return 0xFF9E98A2;
            case BuildingType.CATNIP_FARM:
                return 0xFF8FC15C;
            case BuildingType.WEAVERS_COTTAGE:
                return 0xFFD88FB0;
            case BuildingType.KITTEN_COTTAGE:
                return 0xFFE0A96A;
            case BuildingType.STORAGE_BARN:
                return 0xFFB08A55;
            case BuildingType.SCHOLARS_DEN:
                return 0xFF7C8FC1;
            case BuildingType.COZY_PLAZA:
                return 0xFFE9B65C;
            default:
                return 0xFFAAAAAA;
        }
    }

    private void drawBuildingGlyph(Canvas canvas, int typeId, float cx, float cy) {
        paint.setColor(0xE6FFFFFF);
        switch (typeId) {
            case BuildingType.TOWN_HALL:
                path.reset();
                path.moveTo(cx - 12f, cy + 10f);
                path.lineTo(cx, cy - 12f);
                path.lineTo(cx + 12f, cy + 10f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case BuildingType.FISHING_DOCK:
                canvas.drawArc(cx - 12f, cy - 4f, cx + 12f, cy + 10f, 200f, 140f, false, paint);
                break;
            case BuildingType.LUMBER_CAMP:
                canvas.drawRoundRect(cx - 5f, cy - 4f, cx + 5f, cy + 12f, 2f, 2f, paint);
                canvas.drawCircle(cx, cy - 8f, 8f, paint);
                break;
            case BuildingType.QUARRY:
                canvas.drawCircle(cx - 6f, cy + 4f, 7f, paint);
                canvas.drawCircle(cx + 7f, cy, 9f, paint);
                break;
            case BuildingType.CATNIP_FARM:
                canvas.drawOval(cx - 4f, cy - 12f, cx + 4f, cy + 6f, paint);
                canvas.drawOval(cx - 12f, cy - 2f, cx - 2f, cy + 8f, paint);
                canvas.drawOval(cx + 2f, cy - 2f, cx + 12f, cy + 8f, paint);
                break;
            case BuildingType.WEAVERS_COTTAGE:
                canvas.drawCircle(cx, cy, 9f, paint);
                paint.setColor(0xFFD88FB0);
                canvas.drawCircle(cx, cy, 4f, paint);
                break;
            case BuildingType.KITTEN_COTTAGE:
                path.reset();
                path.moveTo(cx - 11f, cy + 9f);
                path.lineTo(cx, cy - 11f);
                path.lineTo(cx + 11f, cy + 9f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case BuildingType.STORAGE_BARN:
                canvas.drawRoundRect(cx - 10f, cy - 8f, cx + 10f, cy + 11f, 3f, 3f, paint);
                break;
            case BuildingType.SCHOLARS_DEN:
                canvas.drawRoundRect(cx - 10f, cy - 8f, cx + 10f, cy + 9f, 2f, 2f, paint);
                paint.setColor(0xFF7C8FC1);
                canvas.drawLine(cx, cy - 6f, cx, cy + 7f, paint);
                break;
            case BuildingType.COZY_PLAZA:
                canvas.drawCircle(cx, cy, 3f, paint);
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * 2 * i / 6.0;
                    canvas.drawCircle(cx + (float) Math.cos(angle) * 9f, cy + (float) Math.sin(angle) * 9f, 2.2f, paint);
                }
                break;
            default:
                break;
        }
    }

    private void drawGridOverlayIfPlacing(Canvas canvas) {
        if (world.getPendingBuildingTypeId() == BuildingType.NONE) {
            return;
        }
        canvas.save();
        canvas.clipRect(MAP_LEFT, MAP_TOP, MAP_RIGHT, MAP_BOTTOM);
        canvas.translate(MAP_LEFT, MAP_TOP);
        canvas.scale(zoom, zoom);
        canvas.translate(-cameraX, -cameraY);
        float viewportWorldWidth = (MAP_RIGHT - MAP_LEFT) / zoom;
        float viewportWorldHeight = (MAP_BOTTOM - MAP_TOP) / zoom;
        int firstCol = clampInt((int) Math.floor(cameraX / TILE), 0, WorldMap.SIZE - 1);
        int firstRow = clampInt((int) Math.floor(cameraY / TILE), 0, WorldMap.SIZE - 1);
        int lastCol = clampInt((int) Math.ceil((cameraX + viewportWorldWidth) / TILE), 0, WorldMap.SIZE - 1);
        int lastRow = clampInt((int) Math.ceil((cameraY + viewportWorldHeight) / TILE), 0, WorldMap.SIZE - 1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x2A3E5C6B);
        for (int row = firstRow; row <= lastRow; row++) {
            canvas.drawLine(firstCol * TILE, row * TILE, (lastCol + 1) * TILE, row * TILE, paint);
        }
        for (int col = firstCol; col <= lastCol; col++) {
            canvas.drawLine(col * TILE, firstRow * TILE, col * TILE, (lastRow + 1) * TILE, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void drawHud(Canvas canvas, float time) {
        drawPill(canvas, 20f, 16f, 780f, 78f, 0xE0FDFAF0, 0x203E3226);
        ResourceType[] resourceTypes = ResourceType.createAll();
        float slotWidth = 148f;
        for (int i = 0; i < resourceTypes.length; i++) {
            float cx = 55f + i * slotWidth;
            paint.setColor(resourceTypes[i].color);
            canvas.drawCircle(cx, 47f, 11f, paint);
            drawFittedText(canvas, String.valueOf(world.getResource(i)), cx + 42f, 54f,
                    24f, 90f, 0xFF443C2E, true);
        }

        drawPill(canvas, 800f, 16f, 1150f, 78f, 0xDFFDFAF0, 0x1E3E3226);
        drawFittedText(canvas, text(R.string.turn_label) + " " + world.getTurn()
                        + "   " + text(R.string.population_label) + " " + world.getPopulation() + "/" + world.getHousingCap(),
                975f, 54f, 21f, 330f, 0xFF443C2E, true);

        paint.setColor(0xEFFDFAF0);
        canvas.drawCircle(PAUSE_X, PAUSE_Y, 34f, paint);
        paint.setColor(0xFF5A5142);
        canvas.drawRoundRect(PAUSE_X - 9f, PAUSE_Y - 12f, PAUSE_X - 3f, PAUSE_Y + 12f, 3f, 3f, paint);
        canvas.drawRoundRect(PAUSE_X + 3f, PAUSE_Y - 12f, PAUSE_X + 9f, PAUSE_Y + 12f, 3f, 3f, paint);

        drawBottomButton(canvas, BUILD_BTN_CX, text(R.string.build_menu_title), 0xFFEFE3C4);
        drawBottomButton(canvas, RESEARCH_BTN_CX, text(R.string.tech_tree_title), 0xFFC9DCEF);
        drawBottomButton(canvas, END_TURN_BTN_CX, text(R.string.end_turn), 0xFFC7E4C9);
    }

    private void drawBottomButton(Canvas canvas, float cx, String label, int fill) {
        drawPill(canvas, cx - BOTTOM_BTN_HALF_W, BOTTOM_BTN_CY - BOTTOM_BTN_HALF_H,
                cx + BOTTOM_BTN_HALF_W, BOTTOM_BTN_CY + BOTTOM_BTN_HALF_H, fill, 0x1E3E3226);
        drawFittedText(canvas, label, cx, BOTTOM_BTN_CY + 7f, 21f, BOTTOM_BTN_HALF_W * 1.8f, 0xFF443C2E, true);
    }

    private void drawPlacementBanner(Canvas canvas) {
        BuildingType type = world.getBuildingTypes()[world.getPendingBuildingTypeId()];
        drawPill(canvas, 350f, 656f, 930f, 660f + 44f, 0xF0FFFBEE, 0x203E3226);
        drawFittedText(canvas, text(type.nameRes) + " — " + text(R.string.cancel) + "?",
                640f, 685f, 20f, 550f, 0xFF443C2E, true);
    }

    private void drawBuildMenu(Canvas canvas) {
        drawModalScrim(canvas);
        drawModalCard(canvas);
        drawFittedText(canvas, text(R.string.build_menu_title), (MODAL_LEFT + MODAL_RIGHT) / 2f, MODAL_TOP + 56f,
                34f, MODAL_RIGHT - MODAL_LEFT - 100f, 0xFF443C2E, true);

        BuildingType[] types = world.getBuildingTypes();
        int columns = 3;
        float contentLeft = MODAL_LEFT + 60f;
        float contentTop = MODAL_TOP + 90f;
        float cellWidth = (MODAL_RIGHT - MODAL_LEFT - 120f) / columns;
        float cellHeight = 150f;
        int index = 0;
        for (BuildingType type : types) {
            if (!type.playerBuildable) {
                continue;
            }
            int row = index / columns;
            int col = index % columns;
            float cx = contentLeft + (col + 0.5f) * cellWidth;
            float cy = contentTop + row * cellHeight + cellHeight * 0.42f;
            boolean affordable = world.canAffordBuilding(type.id);
            drawPill(canvas, cx - cellWidth * 0.44f, cy - 58f, cx + cellWidth * 0.44f, cy + 58f,
                    affordable ? 0xFFFFFDF6 : 0xFFEDE9DD, 0x14443C2E);
            canvas.save();
            canvas.translate(cx, cy - 28f);
            canvas.scale(1.1f, 1.1f);
            drawBuildingToken(canvas, type.id, 0f, 0f, false, 0f);
            canvas.restore();
            drawFittedText(canvas, text(type.nameRes), cx, cy + 26f, 18f, cellWidth * 0.86f,
                    affordable ? 0xFF443C2E : 0xFF9A9284, true);
            drawFittedText(canvas, formatCost(type), cx, cy + 48f, 14f, cellWidth * 0.86f,
                    affordable ? 0xFF74694F : 0xFFAAA294, false);
            index++;
        }

        drawPill(canvas, (MODAL_LEFT + MODAL_RIGHT) / 2f - 90f, MODAL_BOTTOM - 60f,
                (MODAL_LEFT + MODAL_RIGHT) / 2f + 90f, MODAL_BOTTOM - 20f, 0xFFE9DDCB, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.cancel), (MODAL_LEFT + MODAL_RIGHT) / 2f, MODAL_BOTTOM - 32f,
                20f, 160f, 0xFF443C2E, true);
    }

    private String formatCost(BuildingType type) {
        ResourceType[] resourceTypes = ResourceType.createAll();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < type.cost.length; i++) {
            if (type.cost[i] <= 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("  ");
            }
            builder.append(type.cost[i]).append(' ').append(text(resourceTypes[i].nameRes));
        }
        return builder.length() == 0 ? "" : builder.toString();
    }

    private void drawModalScrim(Canvas canvas) {
        paint.setColor(0x8A4A4234);
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
    }

    private void drawModalCard(Canvas canvas) {
        paint.setColor(0x264A4234);
        canvas.drawRoundRect(MODAL_LEFT + 4f, MODAL_TOP + 7f, MODAL_RIGHT + 4f, MODAL_BOTTOM + 7f, 38f, 38f, paint);
        paint.setColor(0xF8FDFAF0);
        canvas.drawRoundRect(MODAL_LEFT, MODAL_TOP, MODAL_RIGHT, MODAL_BOTTOM, 38f, 38f, paint);
    }

    private void drawPauseOverlay(Canvas canvas, float time) {
        float eased = overlayProgress * overlayProgress * (3f - 2f * overlayProgress);
        paint.setColor(Color.argb(Math.round(120f * eased), 74, 66, 52));
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);

        float cardScale = 0.95f + 0.05f * eased;
        int layer = canvas.saveLayerAlpha(300f, 200f, 980f, 520f, Math.round(255f * eased));
        canvas.scale(cardScale, cardScale, 640f, 360f);
        paint.setColor(0x254A4234);
        canvas.drawRoundRect(304f, 207f, 984f, 527f, 40f, 40f, paint);
        paint.setColor(0xF8FDFAF0);
        canvas.drawRoundRect(300f, 200f, 980f, 520f, 40f, 40f, paint);

        drawFittedText(canvas, text(R.string.paused), 640f, 300f, 36f, 600f, 0xFF443C2E, true);
        drawFittedText(canvas, text(R.string.touch_to_continue), 640f, 355f, 21f, 560f, 0xFF74694F, false);
        drawLanguageSwitch(canvas, 640f, 440f);
        drawPill(canvas, 540f, 472f, 740f, 508f, 0xFFE9DDCB, 0x1A443C2E);
        drawFittedText(canvas, text(R.string.main_menu), 640f, 496f, 18f, 180f, 0xFF443C2E, true);
        canvas.restoreToCount(layer);
    }

    private void drawKittenAtOrigin(Canvas canvas, float time, float facing, boolean moving) {
        canvas.save();
        canvas.scale(facing < 0f ? -1f : 1f, 1f);
        float bounce = moving ? (float) Math.sin(time * 9f) * 2f : (float) Math.sin(time * 2f) * 0.6f;
        canvas.translate(0f, bounce);

        paint.setColor(0x264A4234);
        canvas.drawOval(-26f, 20f, 26f, 30f, paint);

        paint.setColor(0xFFF0A582);
        canvas.drawOval(-22f, -14f, 22f, 22f, paint);
        paint.setColor(0xFFF7C6AA);
        canvas.drawOval(-4f, -6f, 22f, 18f, paint);

        path.reset();
        path.moveTo(-16f, -12f);
        path.lineTo(-20f, -26f);
        path.lineTo(-8f, -16f);
        path.close();
        paint.setColor(0xFFF0A582);
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(10f, -14f);
        path.lineTo(16f, -27f);
        path.lineTo(20f, -12f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0xFF5E5872);
        canvas.drawCircle(-6f, -2f, 2.2f, paint);
        canvas.drawCircle(9f, -2f, 2.2f, paint);
        paint.setColor(0xFFD88391);
        path.reset();
        path.moveTo(0f, 4f);
        path.lineTo(4f, 4f);
        path.lineTo(2f, 7f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(9f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFE99B78);
        path.reset();
        path.moveTo(-18f, 14f);
        path.cubicTo(-34f, 4f, -36f, 20f, -28f, 24f);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0xFFE69673);
        canvas.drawOval(-17f, 18f, -6f, 28f, paint);
        canvas.drawOval(6f, 18f, 17f, 28f, paint);
        canvas.restore();
    }

    private void drawHill(Canvas canvas, int color, float top, float amplitude, float frequency) {
        path.reset();
        path.moveTo(0f, WORLD_HEIGHT);
        path.lineTo(0f, top);
        for (int x = 0; x <= 1280; x += 32) {
            path.lineTo(x, top + (float) Math.sin(x * frequency) * amplitude);
        }
        path.lineTo(WORLD_WIDTH, WORLD_HEIGHT);
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    private void drawPill(Canvas canvas, float left, float top, float right, float bottom,
                          int fillColor, int shadowColor) {
        float radius = (bottom - top) * 0.5f;
        paint.setColor(shadowColor);
        canvas.drawRoundRect(left + 3f, top + 4f, right + 3f, bottom + 4f, radius, radius, paint);
        paint.setColor(fillColor);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    private void drawLanguageSwitch(Canvas canvas, float cx, float cy) {
        drawPill(canvas, cx - 58f, cy - 27f, cx + 58f, cy + 27f, 0xEFFDFAF0, 0x1E3E3226);
        float selectedX = "en".equals(language) ? cx - 28f : cx + 28f;
        paint.setColor(0xFFE9B65C);
        canvas.drawCircle(selectedX, cy, 21f, paint);
        drawFittedText(canvas, "EN", cx - 28f, cy + 7f, 17f, 36f, 0xFF443C2E, true);
        drawFittedText(canvas, "RU", cx + 28f, cy + 7f, 17f, 36f, 0xFF443C2E, true);
    }

    private void drawFittedText(Canvas canvas, String value, float centerX, float baseline,
                                float preferredSize, float maxWidth, int color, boolean useBold) {
        paint.setTypeface(useBold ? bold : regular);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(preferredSize);
        float width = paint.measureText(value);
        if (width > maxWidth && width > 0f) {
            paint.setTextSize(preferredSize * maxWidth / width);
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(value, centerX, baseline, paint);
    }

    private void spawnParticlesWorld(float worldX, float worldY, int color, int count) {
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2f;
            float speed = 30f + random.nextFloat() * 55f;
            particles.add(new Particle(
                    worldX, worldY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 18f,
                    0.6f + random.nextFloat() * 0.5f,
                    2.4f + random.nextFloat() * 3.2f,
                    color
            ));
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.life -= dt;
            if (particle.life <= 0f) {
                particles.remove(i);
                continue;
            }
            particle.x += particle.velocityX * dt;
            particle.y += particle.velocityY * dt;
            particle.velocityY += 26f * dt;
        }
    }

    private void drawParticles(Canvas canvas) {
        for (Particle particle : particles) {
            float alpha = Math.min(1f, particle.life * 2f);
            paint.setColor(Color.argb(Math.round(alpha * 255f),
                    Color.red(particle.color), Color.green(particle.color), Color.blue(particle.color)));
            canvas.drawCircle(particle.x, particle.y, particle.radius * alpha, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (viewScale <= 0f) {
            return true;
        }
        performClick();
        KingdomWorld.State state = world.getState();

        if (state == KingdomWorld.State.TITLE) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                handleTitleTap(toLogicalX(event.getX()), toLogicalY(event.getY()));
            }
            return true;
        }

        if (state == KingdomWorld.State.PAUSED) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                handlePausedTap(toLogicalX(event.getX()), toLogicalY(event.getY()));
            }
            return true;
        }

        if (activeOverlay != Overlay.NONE) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                handleOverlayTap(toLogicalX(event.getX()), toLogicalY(event.getY()));
            }
            return true;
        }

        scaleGestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downCameraX = cameraX;
                downCameraY = cameraY;
                isDragging = false;
                multiTouchOccurred = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiTouchOccurred = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!multiTouchOccurred && event.getPointerCount() == 1) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (!isDragging && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        cameraX = downCameraX - dx / (viewScale * zoom);
                        cameraY = downCameraY - dy / (viewScale * zoom);
                        clampCamera();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!isDragging && !multiTouchOccurred) {
                    handleWorldTap(toLogicalX(event.getX()), toLogicalY(event.getY()));
                }
                isDragging = false;
                break;
            default:
                break;
        }
        return true;
    }

    private float toLogicalX(float rawX) {
        return (rawX - viewOffsetX) / viewScale;
    }

    private float toLogicalY(float rawY) {
        return (rawY - viewOffsetY) / viewScale;
    }

    private void handleTitleTap(float logicalX, float logicalY) {
        if (confirmingNewKingdom) {
            handleNewKingdomConfirmTap(logicalX, logicalY);
            return;
        }
        if (isInsidePill(logicalX, logicalY, 1150f, 56f, 58f, 27f)) {
            toggleLanguage();
            return;
        }
        float menuTop = MENU_TOP;
        if (hasSaveFile) {
            if (isInsideMenuButton(logicalX, logicalY, menuTop)) {
                loadAndContinue();
                centerCameraOnStart();
                invalidate();
                return;
            }
            menuTop += MENU_BTN_HEIGHT + MENU_BTN_GAP;
        }
        if (isInsideMenuButton(logicalX, logicalY, menuTop)) {
            if (hasSaveFile) {
                confirmingNewKingdom = true;
            } else {
                world.beginNewKingdom();
                centerCameraOnStart();
            }
            invalidate();
            return;
        }
        menuTop += MENU_BTN_HEIGHT + MENU_BTN_GAP;
        if (isInsideMenuButton(logicalX, logicalY, menuTop)) {
            openHelp();
        }
    }

    private boolean isInsideMenuButton(float x, float y, float top) {
        return isInsidePill(x, y, MENU_BTN_CX, top + MENU_BTN_HEIGHT / 2f, MENU_BTN_HALF_W, MENU_BTN_HEIGHT / 2f);
    }

    private void handleNewKingdomConfirmTap(float logicalX, float logicalY) {
        if (isInsidePill(logicalX, logicalY, 525f, 419f, 105f, 27f)) {
            confirmingNewKingdom = false;
            world.beginNewKingdom();
            centerCameraOnStart();
        } else if (isInsidePill(logicalX, logicalY, 755f, 419f, 105f, 27f)) {
            confirmingNewKingdom = false;
        }
        invalidate();
    }

    private void handlePausedTap(float logicalX, float logicalY) {
        if (isInsidePill(logicalX, logicalY, 640f, 440f, 58f, 27f)) {
            toggleLanguage();
            invalidate();
            return;
        }
        if (isInsidePill(logicalX, logicalY, 640f, 490f, 100f, 18f)) {
            exitToMainMenu();
            return;
        }
        world.resume();
        invalidate();
    }

    private void handleOverlayTap(float logicalX, float logicalY) {
        float cancelCx = (MODAL_LEFT + MODAL_RIGHT) / 2f;
        float cancelCy = MODAL_BOTTOM - 40f;
        if (isInsidePill(logicalX, logicalY, cancelCx, cancelCy, 90f, 20f)) {
            activeOverlay = Overlay.NONE;
            invalidate();
            return;
        }
        if (activeOverlay == Overlay.BUILD_MENU) {
            handleBuildMenuTap(logicalX, logicalY);
        }
        invalidate();
    }

    private void handleBuildMenuTap(float logicalX, float logicalY) {
        BuildingType[] types = world.getBuildingTypes();
        int columns = 3;
        float contentLeft = MODAL_LEFT + 60f;
        float contentTop = MODAL_TOP + 90f;
        float cellWidth = (MODAL_RIGHT - MODAL_LEFT - 120f) / columns;
        float cellHeight = 150f;
        int index = 0;
        for (BuildingType type : types) {
            if (!type.playerBuildable) {
                continue;
            }
            int row = index / columns;
            int col = index % columns;
            float cx = contentLeft + (col + 0.5f) * cellWidth;
            float cy = contentTop + row * cellHeight + cellHeight * 0.42f;
            if (isInsidePill(logicalX, logicalY, cx, cy, cellWidth * 0.44f, 58f)) {
                if (world.canAffordBuilding(type.id)) {
                    world.selectBuildingForPlacement(type.id);
                    activeOverlay = Overlay.NONE;
                }
                return;
            }
            index++;
        }
    }

    private void handleWorldTap(float logicalX, float logicalY) {
        if (isInsidePill(logicalX, logicalY, PAUSE_X, PAUSE_Y, 34f, 34f)) {
            world.pause();
            saveKingdom();
            invalidate();
            return;
        }
        if (isInsidePill(logicalX, logicalY, BUILD_BTN_CX, BOTTOM_BTN_CY, BOTTOM_BTN_HALF_W, BOTTOM_BTN_HALF_H)) {
            activeOverlay = Overlay.BUILD_MENU;
            invalidate();
            return;
        }
        if (isInsidePill(logicalX, logicalY, RESEARCH_BTN_CX, BOTTOM_BTN_CY, BOTTOM_BTN_HALF_W, BOTTOM_BTN_HALF_H)) {
            openTechTree();
            return;
        }
        if (isInsidePill(logicalX, logicalY, END_TURN_BTN_CX, BOTTOM_BTN_CY, BOTTOM_BTN_HALF_W, BOTTOM_BTN_HALF_H)) {
            world.endTurn();
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            announceForAccessibility(text(R.string.accessibility_turn_ended));
            invalidate();
            return;
        }
        if (world.getPendingBuildingTypeId() != BuildingType.NONE
                && isInsidePill(logicalX, logicalY, 640f, 678f, 290f, 22f)) {
            world.cancelPlacement();
            invalidate();
            return;
        }
        if (logicalX < MAP_LEFT || logicalX > MAP_RIGHT || logicalY < MAP_TOP || logicalY > MAP_BOTTOM) {
            return;
        }
        float worldX = cameraX + (logicalX - MAP_LEFT) / zoom;
        float worldY = cameraY + (logicalY - MAP_TOP) / zoom;
        int col = (int) Math.floor(worldX / TILE);
        int row = (int) Math.floor(worldY / TILE);
        if (world.tapCell(row, col)) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        invalidate();
    }

    private boolean isInsidePill(float x, float y, float centerX, float centerY, float halfW, float halfH) {
        return Math.abs(x - centerX) <= halfW && Math.abs(y - centerY) <= halfH;
    }

    private void clampCamera() {
        float mapWorldSize = WorldMap.SIZE * TILE;
        float viewportWorldWidth = (MAP_RIGHT - MAP_LEFT) / zoom;
        float viewportWorldHeight = (MAP_BOTTOM - MAP_TOP) / zoom;
        float maxCameraX = Math.max(0f, mapWorldSize - viewportWorldWidth);
        float maxCameraY = Math.max(0f, mapWorldSize - viewportWorldHeight);
        cameraX = clampFloat(cameraX, 0f, maxCameraX);
        cameraY = clampFloat(cameraY, 0f, maxCameraY);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void toggleLanguage() {
        language = "en".equals(language) ? "ru" : "en";
        preferences.edit().putString(PREF_LANGUAGE, language).apply();
        applyLanguage(language);
        setContentDescription(text(R.string.accessibility_game));
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        invalidate();
    }

    private void applyLanguage(String languageCode) {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(languageCode));
        localizedContext = getContext().createConfigurationContext(configuration);
    }

    private String text(int resource) {
        return localizedContext.getString(resource);
    }

    private void loadAndContinue() {
        try (InputStream in = new FileInputStream(saveFile)) {
            KingdomSaveData data = KingdomSerializer.read(in);
            world.continueKingdom(data);
        } catch (IOException failure) {
            world.beginNewKingdom();
        }
    }

    private void exitToMainMenu() {
        world.showTitle();
        saveKingdom();
        invalidate();
    }

    private void saveKingdom() {
        if (world.getState() == KingdomWorld.State.TITLE) {
            return;
        }
        try (OutputStream out = new FileOutputStream(saveFile)) {
            KingdomSerializer.write(world.snapshot(), out);
            hasSaveFile = true;
        } catch (IOException ignored) {
            // Autosave is best-effort; losing one save must never crash or block play.
        }
    }

    @SuppressWarnings("deprecation")
    private void openTechTree() {
        Intent intent = new Intent(getContext(), TechTreeActivity.class);
        intent.putExtra(TechTreeActivity.EXTRA_TECH_POINTS, world.getTechPointPool());
        intent.putExtra(TechTreeActivity.EXTRA_ACTIVE_TECH, world.getActiveTechId());
        intent.putExtra(TechTreeActivity.EXTRA_UNLOCKED_BITS, world.getTechUnlockedBits());
        intent.putExtra(TechTreeActivity.EXTRA_LANGUAGE, language);
        ((Activity) getContext()).startActivityForResult(intent, TechTreeActivity.REQUEST_CODE);
    }

    /** Applies the technology chosen in {@link TechTreeActivity}, forwarded via activity result. */
    void applyTechSelection(int techId) {
        if (techId != TechNode.NONE) {
            world.selectActiveTech(techId);
        }
        invalidate();
    }

    private void openHelp() {
        Intent intent = new Intent(getContext(), HelpActivity.class);
        intent.putExtra(HelpActivity.EXTRA_LANGUAGE, language);
        getContext().startActivity(intent);
    }

    boolean handleBack() {
        KingdomWorld.State state = world.getState();
        if (confirmingNewKingdom) {
            confirmingNewKingdom = false;
            invalidate();
            return true;
        }
        if (activeOverlay != Overlay.NONE) {
            activeOverlay = Overlay.NONE;
            invalidate();
            return true;
        }
        if (world.getPendingBuildingTypeId() != BuildingType.NONE) {
            world.cancelPlacement();
            invalidate();
            return true;
        }
        if (state == KingdomWorld.State.PLAYING) {
            world.pause();
            saveKingdom();
            invalidate();
            return true;
        }
        if (state == KingdomWorld.State.PAUSED) {
            exitToMainMenu();
            return true;
        }
        return false;
    }

    void onHostPause() {
        hostResumed = false;
        music.setPlaying(false);
        lastFrameNanos = 0L;
        world.pause();
        saveKingdom();
    }

    void onHostResume() {
        hostResumed = true;
        lastFrameNanos = 0L;
        invalidate();
    }

    void close() {
        hostResumed = false;
        music.close();
        audio.close();
    }

    @Override
    public void onTurnResolved(int[] resourceDeltas) {
        // Intentionally quiet: a sound on every single turn would be repetitive at this pace.
    }

    @Override
    public void onBuildingCompleted(int buildingTypeId, int row, int col) {
        audio.playBuildingCompleted(completedBuildingsCount++);
        spawnParticlesWorld(col * TILE + TILE / 2f, row * TILE + TILE / 2f, 0xFFE9B65C, 14);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        announceForAccessibility(text(R.string.accessibility_building_placed));
    }

    @Override
    public void onTechUnlocked(int techId) {
        audio.playTechUnlocked();
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        announceForAccessibility(text(R.string.accessibility_tech_unlocked));
    }

    @Override
    public void onPopulationGrew(int newPopulation) {
        audio.playPopulationGrew();
    }

    private static int lighten(int color, float amount) {
        int red = Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * amount));
        int green = Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * amount));
        int blue = Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount));
        return Color.rgb(red, green, blue);
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float oldZoom = zoom;
            zoom = clampFloat(zoom * detector.getScaleFactor(), MIN_ZOOM, MAX_ZOOM);
            float viewportWidth = MAP_RIGHT - MAP_LEFT;
            float viewportHeight = MAP_BOTTOM - MAP_TOP;
            float centerWorldX = cameraX + viewportWidth / (2f * oldZoom);
            float centerWorldY = cameraY + viewportHeight / (2f * oldZoom);
            cameraX = centerWorldX - viewportWidth / (2f * zoom);
            cameraY = centerWorldY - viewportHeight / (2f * zoom);
            clampCamera();
            return true;
        }
    }

    private static final class Particle {
        float x;
        float y;
        final float velocityX;
        float velocityY;
        float life;
        final float maxLife;
        final float radius;
        final int color;

        Particle(float x, float y, float velocityX, float velocityY,
                 float life, float radius, int color) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.life = life;
            this.maxLife = life;
            this.radius = radius;
            this.color = color;
        }
    }
}
