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
import android.graphics.Bitmap;
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
import java.util.ArrayDeque;
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
        BUILD_MENU,
        WORLD_MAP,
        BUILDING_PANEL
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
    private static final float BUILD_BTN_CX = 200f;
    private static final float RESEARCH_BTN_CX = 470f;
    private static final float WORLD_MAP_BTN_CX = 740f;
    private static final float END_TURN_BTN_CX = 1010f;
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
    private static final float NOTIFICATION_DURATION = 2.4f;
    private static final int BUILDINGS_PER_PAGE = 4;
    private static final float OVERLAY_SWIPE_THRESHOLD = 72f;
    private static final int WILDLIFE_PER_SPECIES = 2;
    private static final int WILDLIFE_VISIBLE_RADIUS = 6;
    private static final String PREFS = "kitten_kingdoms_progress";
    private static final String PREF_LANGUAGE = "language";
    private static final String SAVE_FILE_NAME = "kingdom.sav";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint spritePaint = new Paint();
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final TerrainSprites sprites;
    private final KittenSprites kittenSprites;
    private final WildlifeSprites wildlifeSprites;
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final SharedPreferences preferences;
    private final AudioEngine audio = new AudioEngine();
    private final MusicEngine music;
    private final KingdomWorld world;
    private final List<Particle> particles = new ArrayList<>();
    private final List<WildlifeCritter> wildlifeCritters = new ArrayList<>();
    private final Random random = new Random(0xA17E5501L);
    private final Random wildlifeRandom = new Random(0xC0C04A11L);
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
    private float overlayDownX;
    private float overlayDownY;
    private int buildMenuPage;
    private Overlay activeOverlay = Overlay.NONE;
    private KingdomWorld.State lastVisualState = KingdomWorld.State.TITLE;
    private float overlayProgress = 1f;
    private int completedBuildingsCount;
    private boolean confirmingNewKingdom;
    private int rejectionReason = KingdomWorld.PLACEMENT_OK;
    private float rejectionMessageUntil;
    private final ArrayDeque<String> pendingNotifications = new ArrayDeque<>();
    private String currentNotificationText;
    private float currentNotificationRemaining;
    private int selectedSettlementId = Settlement.RIVERWHISKER;
    private int selectedBuildingId = BuildingType.NONE;

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
        spritePaint.setFilterBitmap(false);
        spritePaint.setAntiAlias(false);
        spritePaint.setDither(false);
        sprites = new TerrainSprites(context.getResources());
        kittenSprites = new KittenSprites(context.getResources());
        wildlifeSprites = new WildlifeSprites(context.getResources());
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
        KingdomWorld.State visualState = world.getState();
        if (hostResumed) {
            world.update(dt);
            updateParticles(dt);
            updateNotifications(dt);
            if (visualState == KingdomWorld.State.PLAYING) {
                updateWildlife(dt);
            }
        }

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
        drawKittenAtOrigin(canvas, time, KittenSprites.DOWN, false);
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

        if (activeOverlay == Overlay.WORLD_MAP) {
            drawDiplomacyWorldMap(canvas, time);
            drawHud(canvas, time);
            drawNotificationBanner(canvas);
            if (world.getState() == KingdomWorld.State.PAUSED) {
                drawPauseOverlay(canvas, time);
            }
            return;
        }

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
        drawNotificationBanner(canvas);

        if (world.getPendingBuildingTypeId() != BuildingType.NONE) {
            drawPlacementBanner(canvas, time);
        }

        if (activeOverlay == Overlay.BUILD_MENU) {
            drawBuildMenu(canvas);
        }
        if (activeOverlay == Overlay.BUILDING_PANEL) {
            drawBuildingPanel(canvas);
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
            drawBuildingStatus(canvas, building, cx, cy, time);
        }

        for (WorkerKitten worker : world.getWorkers()) {
            if (worker.state == WorkerKitten.DIPLOMACY) {
                continue;
            }
            int workerRow = (int) worker.visualRow;
            int workerCol = (int) worker.visualCol;
            if (workerRow < firstRow || workerRow > lastRow
                    || workerCol < firstCol || workerCol > lastCol
                    || !map.isExplored(worker.row, worker.col)) {
                continue;
            }
            float workerX = worker.visualCol * TILE + TILE / 2f;
            float workerY = worker.visualRow * TILE + TILE / 2f;
            canvas.save();
            canvas.translate(workerX, workerY);
            canvas.scale(0.82f, 0.82f);
            drawKittenAtOrigin(canvas, time + worker.id * 0.37f,
                    worker.facingDirection, worker.isMoving());
            paint.setColor(workerColor(worker.id));
            canvas.drawCircle(22f, -22f, 7f, paint);
            if (worker.state == WorkerKitten.CONSTRUCTING) {
                drawHammer(canvas, 0f, -50f, 0.9f);
            } else if (worker.carriedAmount > 0) {
                drawResourceBubble(canvas, worker.carriedResourceId, worker.carriedAmount,
                        0f, -56f, 0.92f);
            }
            canvas.restore();
        }

        for (WildlifeCritter critter : wildlifeCritters) {
            int critterRow = (int) (critter.worldY / TILE);
            int critterCol = (int) (critter.worldX / TILE);
            if (critterRow < firstRow || critterRow > lastRow
                    || critterCol < firstCol || critterCol > lastCol
                    || !map.isExplored(critterRow, critterCol)) {
                continue;
            }
            canvas.save();
            canvas.translate(critter.worldX, critter.worldY);
            canvas.scale(0.95f, 0.95f);
            drawWildlifeCritter(canvas, critter, time);
            canvas.restore();
        }

        drawParticles(canvas);

        float kittenX = world.getVisualCol() * TILE + TILE / 2f;
        float kittenY = world.getVisualRow() * TILE + TILE / 2f;
        canvas.save();
        canvas.translate(kittenX, kittenY);
        drawKittenAtOrigin(canvas, time, world.getFacingDirection(), world.getPathLength() > 0);
        canvas.restore();
    }

    private void drawTile(Canvas canvas, WorldMap map, int row, int col) {
        float left = col * TILE;
        float top = row * TILE;
        int seedValue = map.visualSeedAt(row, col);
        if (!map.isExplored(row, col)) {
            canvas.drawBitmap(sprites.fogTile(seedValue), left, top, spritePaint);
            paint.setColor(0xB85F6670);
            canvas.drawRect(left, top, left + TILE, top + TILE, paint);
            return;
        }
        int terrainId = map.terrainAt(row, col);
        canvas.drawBitmap(sprites.terrainFor(map, row, col, seedValue), left, top, spritePaint);

        if (terrainId == TerrainType.WATER) {
            Bitmap lily = sprites.waterLilyFor(seedValue);
            if (lily != null) {
                float lilySize = TILE * 0.5f;
                canvas.drawBitmap(lily, null,
                        new RectF(left + (TILE - lilySize) / 2f, top + (TILE - lilySize) / 2f,
                                left + (TILE + lilySize) / 2f, top + (TILE + lilySize) / 2f),
                        spritePaint);
            }
        } else {
            Bitmap prop = sprites.propFor(terrainId, seedValue);
            if (prop != null) {
                canvas.drawBitmap(prop, left + (TILE - prop.getWidth()) / 2f, top + TILE - prop.getHeight(),
                        spritePaint);
            }
        }
    }

    private void drawBuildingToken(Canvas canvas, int typeId, float cx, float cy, boolean underConstruction, float time) {
        BuildingType type = world.getBuildingTypes()[typeId];
        Bitmap sprite = sprites.buildingFor(typeId);
        float maxWidth = typeId == BuildingType.TOWN_HALL || typeId == BuildingType.KITTEN_COTTAGE
                ? 78f : 58f;
        float maxHeight = typeId == BuildingType.TOWN_HALL || typeId == BuildingType.KITTEN_COTTAGE
                ? 78f : 66f;
        float scale = Math.min(maxWidth / sprite.getWidth(), maxHeight / sprite.getHeight());
        float width = sprite.getWidth() * scale;
        float height = sprite.getHeight() * scale;
        float bottom = cy + 30f;
        paint.setColor(0x36433B31);
        canvas.drawOval(cx - width * 0.38f, bottom - 7f, cx + width * 0.38f, bottom + 3f, paint);
        spritePaint.setAlpha(underConstruction ? 150 : 255);
        canvas.drawBitmap(sprite, null,
                new RectF(cx - width / 2f, bottom - height, cx + width / 2f, bottom), spritePaint);
        spritePaint.setAlpha(255);
        if (underConstruction) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(0xE6FFF5CF);
            float sweep = ((float) Math.sin(time * 3f) * 0.5f + 0.5f) * 300f + 30f;
            canvas.drawArc(cx - 25f, cy - 25f, cx + 25f, cy + 25f, -90f, sweep, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (typeId == BuildingType.TOWN_HALL && world.isTechUnlocked(TechNode.KINGDOM_CHARTER)) {
            paint.setColor(0xFFE9B65C);
            path.reset();
            path.moveTo(cx - 10f, cy - 28f);
            path.lineTo(cx, cy - 40f);
            path.lineTo(cx + 10f, cy - 28f);
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    private void drawBuildingStatus(Canvas canvas, PlacedBuilding building, float cx, float cy, float time) {
        if (building.hasReadyGoods()) {
            float bob = (float) Math.sin(time * 3f + building.id) * 2f;
            drawResourceBubble(canvas, building.pendingResourceId, building.pendingAmount,
                    cx, cy - 43f + bob, 1f);
            return;
        }
        if (!building.isComplete() && world.getConstructionWorker(building.id) == null) {
            paint.setColor(0xF8FFF9E9);
            canvas.drawCircle(cx, cy - 38f, 17f, paint);
            drawFittedText(canvas, "!", cx, cy - 31f, 22f, 20f, 0xFFD07062, true);
        } else if (world.needsWorker(building.id)) {
            paint.setColor(0xF8FFF9E9);
            canvas.drawCircle(cx, cy - 38f, 17f, paint);
            paint.setColor(0xFF8A7357);
            canvas.drawCircle(cx - 3f, cy - 42f, 5f, paint);
            canvas.drawRoundRect(cx - 10f, cy - 37f, cx + 4f, cy - 28f, 5f, 5f, paint);
            paint.setStrokeWidth(3f);
            canvas.drawLine(cx + 8f, cy - 37f, cx + 8f, cy - 27f, paint);
            canvas.drawLine(cx + 3f, cy - 32f, cx + 13f, cy - 32f, paint);
        }
    }

    private void drawResourceBubble(Canvas canvas, int resourceId, int amount,
                                    float cx, float cy, float scale) {
        paint.setColor(0xEFFFFAEF);
        canvas.drawRoundRect(cx - 27f * scale, cy - 17f * scale,
                cx + 27f * scale, cy + 17f * scale, 17f * scale, 17f * scale, paint);
        drawResourceIcon(canvas, resourceId, cx - 10f * scale, cy, 12f * scale);
        drawFittedText(canvas, String.valueOf(amount), cx + 13f * scale, cy + 6f * scale,
                15f * scale, 22f * scale, 0xFF443C2E, true);
    }

    private void drawHammer(Canvas canvas, float cx, float cy, float scale) {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(5f * scale);
        paint.setColor(0xFF9B7550);
        canvas.drawLine(cx - 7f * scale, cy + 9f * scale,
                cx + 5f * scale, cy - 8f * scale, paint);
        paint.setStrokeWidth(8f * scale);
        paint.setColor(0xFFA8A2AC);
        canvas.drawLine(cx - 2f * scale, cy - 11f * scale,
                cx + 11f * scale, cy - 2f * scale, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private int workerColor(int workerId) {
        int[] colors = {0xFFD88FB0, 0xFF6FB0D8, 0xFF8FC15C, 0xFFE9B65C, 0xFF8E7CD8};
        return colors[Math.floorMod(workerId, colors.length)];
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
        float slotWidth = 740f / resourceTypes.length;
        for (int i = 0; i < resourceTypes.length; i++) {
            float cx = 55f + i * slotWidth;
            drawResourceIcon(canvas, i, cx, 47f, 13f);
            drawFittedText(canvas, String.valueOf(world.getResource(i)), cx + 42f, 54f,
                    24f, 90f, 0xFF443C2E, true);
        }

        drawPill(canvas, 800f, 16f, 1150f, 78f, 0xDFFDFAF0, 0x1E3E3226);
        drawFittedText(canvas, text(R.string.turn_label) + " " + world.getTurn()
                        + "   " + text(R.string.population_label) + " " + world.getPopulation() + "/" + world.getHousingCap()
                        + "   " + text(R.string.workers_short) + " " + world.getWorkerCount(),
                975f, 54f, 21f, 330f, 0xFF443C2E, true);

        paint.setColor(0xEFFDFAF0);
        canvas.drawCircle(PAUSE_X, PAUSE_Y, 34f, paint);
        paint.setColor(0xFF5A5142);
        canvas.drawRoundRect(PAUSE_X - 9f, PAUSE_Y - 12f, PAUSE_X - 3f, PAUSE_Y + 12f, 3f, 3f, paint);
        canvas.drawRoundRect(PAUSE_X + 3f, PAUSE_Y - 12f, PAUSE_X + 9f, PAUSE_Y + 12f, 3f, 3f, paint);

        drawBottomButton(canvas, BUILD_BTN_CX, text(R.string.build_menu_title), 0xFFEFE3C4);
        drawBottomButton(canvas, RESEARCH_BTN_CX, text(R.string.tech_tree_title), 0xFFC9DCEF);
        drawBottomButton(canvas, WORLD_MAP_BTN_CX, text(R.string.world_map_button), 0xFFE7D3EC);
        drawBottomButton(canvas, END_TURN_BTN_CX, text(R.string.end_turn), 0xFFC7E4C9);
    }

    private void drawResourceIcon(Canvas canvas, int resourceId, float cx, float cy, float size) {
        paint.setStyle(Paint.Style.FILL);
        switch (resourceId) {
            case ResourceType.FISH:
                paint.setColor(0xFF6FB0D8);
                canvas.drawOval(cx - size * 0.7f, cy - size * 0.38f,
                        cx + size * 0.45f, cy + size * 0.38f, paint);
                path.reset();
                path.moveTo(cx + size * 0.35f, cy);
                path.lineTo(cx + size * 0.9f, cy - size * 0.5f);
                path.lineTo(cx + size * 0.9f, cy + size * 0.5f);
                path.close();
                canvas.drawPath(path, paint);
                paint.setColor(0xFFF9F5E8);
                canvas.drawCircle(cx - size * 0.38f, cy - size * 0.1f, size * 0.1f, paint);
                break;
            case ResourceType.WOOD:
                paint.setColor(0xFF9B7550);
                canvas.drawRoundRect(cx - size * 0.8f, cy - size * 0.38f,
                        cx + size * 0.7f, cy + size * 0.38f, size * 0.34f, size * 0.34f, paint);
                paint.setColor(0xFFD2A56F);
                canvas.drawCircle(cx + size * 0.66f, cy, size * 0.32f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.5f, size * 0.1f));
                paint.setColor(0xFF8B6545);
                canvas.drawCircle(cx + size * 0.66f, cy, size * 0.17f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            case ResourceType.STONE:
                paint.setColor(0xFFA8A2AC);
                path.reset();
                path.moveTo(cx - size * 0.8f, cy + size * 0.45f);
                path.lineTo(cx - size * 0.48f, cy - size * 0.5f);
                path.lineTo(cx + size * 0.3f, cy - size * 0.72f);
                path.lineTo(cx + size * 0.82f, cy + size * 0.15f);
                path.lineTo(cx + size * 0.42f, cy + size * 0.55f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case ResourceType.CATNIP:
                paint.setColor(0xFF8FC15C);
                canvas.save();
                canvas.rotate(-32f, cx, cy);
                canvas.drawOval(cx - size * 0.65f, cy - size * 0.22f,
                        cx + size * 0.1f, cy + size * 0.32f, paint);
                canvas.drawOval(cx - size * 0.05f, cy - size * 0.42f,
                        cx + size * 0.72f, cy + size * 0.12f, paint);
                canvas.restore();
                break;
            case ResourceType.YARN:
                paint.setColor(0xFFD88FB0);
                canvas.drawCircle(cx, cy, size * 0.72f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.5f, size * 0.11f));
                paint.setColor(0xFFF9D7E7);
                canvas.drawArc(cx - size * 0.55f, cy - size * 0.38f,
                        cx + size * 0.55f, cy + size * 0.38f, -20f, 215f, false, paint);
                canvas.drawLine(cx - size * 0.45f, cy + size * 0.35f,
                        cx + size * 0.48f, cy - size * 0.3f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            case ResourceType.CRYSTALS:
            default:
                paint.setColor(0xFF8E7CD8);
                path.reset();
                path.moveTo(cx, cy - size * 0.9f);
                path.lineTo(cx + size * 0.62f, cy - size * 0.1f);
                path.lineTo(cx + size * 0.32f, cy + size * 0.82f);
                path.lineTo(cx - size * 0.5f, cy + size * 0.45f);
                path.lineTo(cx - size * 0.62f, cy - size * 0.2f);
                path.close();
                canvas.drawPath(path, paint);
                break;
        }
    }

    private void drawBottomButton(Canvas canvas, float cx, String label, int fill) {
        drawPill(canvas, cx - BOTTOM_BTN_HALF_W, BOTTOM_BTN_CY - BOTTOM_BTN_HALF_H,
                cx + BOTTOM_BTN_HALF_W, BOTTOM_BTN_CY + BOTTOM_BTN_HALF_H, fill, 0x1E3E3226);
        drawFittedText(canvas, label, cx, BOTTOM_BTN_CY + 7f, 21f, BOTTOM_BTN_HALF_W * 1.8f, 0xFF443C2E, true);
    }

    /** Full regional layer: travelling diplomats and persistent trade routes, not a popup list. */
    private void drawDiplomacyWorldMap(Canvas canvas, float time) {
        canvas.save();
        canvas.clipRect(MAP_LEFT, MAP_TOP, MAP_RIGHT, MAP_BOTTOM);
        for (float y = MAP_TOP; y < MAP_BOTTOM; y += TILE) {
            for (float x = MAP_LEFT; x < MAP_RIGHT; x += TILE) {
                int seed = Math.round(x * 3f + y * 7f);
                canvas.drawBitmap(sprites.fogTile(seed), x, y, spritePaint);
            }
        }
        paint.setColor(0xC8F6EBCB);
        canvas.drawRect(MAP_LEFT, MAP_TOP, MAP_RIGHT, MAP_BOTTOM, paint);

        drawFittedText(canvas, text(R.string.world_map_title), 430f, 130f,
                34f, 700f, 0xFF443C2E, true);
        drawFittedText(canvas, text(R.string.world_map_subtitle), 430f, 158f,
                16f, 710f, 0xFF74694F, false);

        float homeX = 445f;
        float homeY = 365f;
        Settlement[] settlements = world.getSettlements();
        for (Settlement settlement : settlements) {
            drawDiplomaticRoute(canvas, homeX, homeY, settlement, time);
        }

        paint.setColor(0x34433B31);
        canvas.drawOval(homeX - 38f, homeY + 31f, homeX + 38f, homeY + 43f, paint);
        Bitmap homeKitten = kittenSprites.frame(KittenSprites.DOWN, 0);
        canvas.drawBitmap(homeKitten, null,
                new RectF(homeX - 35f, homeY - 35f, homeX + 35f, homeY + 35f), spritePaint);
        drawPill(canvas, homeX - 78f, homeY + 42f, homeX + 78f, homeY + 72f,
                0xF5FFF8E8, 0x183E3226);
        drawFittedText(canvas, text(R.string.our_kingdom), homeX, homeY + 63f,
                16f, 142f, 0xFF443C2E, true);

        for (Settlement settlement : settlements) {
            drawSettlementNode(canvas, settlement);
        }
        drawSettlementPanel(canvas, settlements[selectedSettlementId]);
        canvas.restore();
    }

    private void drawDiplomaticRoute(Canvas canvas, float homeX, float homeY,
            Settlement settlement, float time) {
        boolean active = world.hasTradeRoute(settlement.id);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(active ? 6f : 3f);
        paint.setColor(active ? 0xD9D6A13B : 0x8A8D806A);
        if (active) {
            canvas.drawLine(homeX, homeY, settlement.mapX, settlement.mapY, paint);
        } else {
            for (int segment = 0; segment < 18; segment += 2) {
                float start = segment / 18f;
                float end = Math.min(1f, (segment + 1) / 18f);
                canvas.drawLine(lerp(homeX, settlement.mapX, start), lerp(homeY, settlement.mapY, start),
                        lerp(homeX, settlement.mapX, end), lerp(homeY, settlement.mapY, end), paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);

        drawMissionKitten(canvas, homeX, homeY, settlement, time,
                world.getEnvoyWorkerId(settlement.id), world.getEnvoyPhase(settlement.id),
                world.getEnvoyRouteProgress(settlement.id), -9f);
        drawMissionKitten(canvas, homeX, homeY, settlement, time + 0.4f,
                world.getCourierWorkerId(settlement.id), world.getCourierPhase(settlement.id),
                world.getCourierRouteProgress(settlement.id), 9f);
    }

    private void drawMissionKitten(Canvas canvas, float homeX, float homeY, Settlement settlement,
            float time, int workerId, int phase, float progress, float offset) {
        if (workerId == BuildingType.NONE || phase == DiplomacySystem.MISSION_NONE) {
            return;
        }
        float deltaX = settlement.mapX - homeX;
        float deltaY = settlement.mapY - homeY;
        float length = Math.max(1f, (float) Math.hypot(deltaX, deltaY));
        float visibleProgress = phase == DiplomacySystem.MISSION_VISITING ? 0.86f : progress;
        float x = lerp(homeX, settlement.mapX, visibleProgress) - deltaY / length * offset;
        float y = lerp(homeY, settlement.mapY, visibleProgress) + deltaX / length * offset;
        int direction = deltaX >= 0f ? KittenSprites.RIGHT : KittenSprites.LEFT;
        if (phase == DiplomacySystem.MISSION_RETURNING) {
            direction = deltaX >= 0f ? KittenSprites.LEFT : KittenSprites.RIGHT;
        }
        paint.setColor(0x70443C2E);
        canvas.drawOval(x - 17f, y + 13f, x + 17f, y + 20f, paint);
        Bitmap kitten = kittenSprites.frame(direction, (int) (time * 5f));
        canvas.drawBitmap(kitten, null, new RectF(x - 22f, y - 25f, x + 22f, y + 19f),
                spritePaint);
        paint.setColor(workerColor(workerId));
        canvas.drawCircle(x + 15f, y - 16f, 5f, paint);
    }

    private void drawSettlementNode(Canvas canvas, Settlement settlement) {
        boolean selected = settlement.id == selectedSettlementId;
        float cx = settlement.mapX;
        float cy = settlement.mapY;
        paint.setColor(selected ? 0xFFFDF6DE : 0xDDEEE3C4);
        canvas.drawCircle(cx, cy, selected ? 51f : 44f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(selected ? 5f : 3f);
        paint.setColor(settlement.color);
        canvas.drawCircle(cx, cy, selected ? 51f : 44f, paint);
        paint.setStyle(Paint.Style.FILL);
        Bitmap icon = sprites.settlementFor(settlement.id);
        float maxSize = selected ? 72f : 62f;
        float scale = Math.min(maxSize / icon.getWidth(), maxSize / icon.getHeight());
        float width = icon.getWidth() * scale;
        float height = icon.getHeight() * scale;
        canvas.drawBitmap(icon, null,
                new RectF(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f),
                spritePaint);
        drawPill(canvas, cx - 84f, cy + 54f, cx + 84f, cy + 84f,
                0xF5FFF9E9, 0x183E3226);
        drawFittedText(canvas, text(settlement.nameRes), cx, cy + 75f,
                15f, 154f, 0xFF443C2E, true);
    }

    private void drawSettlementPanel(Canvas canvas, Settlement settlement) {
        float left = 855f;
        float right = 1238f;
        float center = (left + right) / 2f;
        paint.setColor(0x2A443C2E);
        canvas.drawRoundRect(left + 4f, 114f, right + 4f, 628f, 28f, 28f, paint);
        paint.setColor(0xF8FFF9E9);
        canvas.drawRoundRect(left, 108f, right, 622f, 28f, 28f, paint);

        drawFittedText(canvas, text(settlement.nameRes), center, 153f,
                28f, 330f, 0xFF443C2E, true);
        drawFittedText(canvas, text(settlement.descriptionRes), center, 181f,
                15f, 330f, 0xFF74694F, false);

        int relation = world.getRelation(settlement.id);
        drawFittedText(canvas, text(R.string.relation_label) + " · "
                        + text(relationStatusRes(relation)), center, 221f,
                17f, 320f, 0xFF5D5546, true);
        paint.setColor(0xFFE4DDC9);
        canvas.drawRoundRect(left + 34f, 236f, right - 34f, 252f, 8f, 8f, paint);
        paint.setColor(settlement.color);
        canvas.drawRoundRect(left + 34f, 236f,
                left + 34f + (right - left - 68f) * relation / 100f, 252f, 8f, 8f, paint);
        drawFittedText(canvas, relation + "/100", center, 275f,
                15f, 180f, 0xFF74694F, false);

        String envoyStatus = missionStatus(world.getEnvoyWorkerId(settlement.id),
                world.getEnvoyPhase(settlement.id));
        String courierStatus = missionStatus(world.getCourierWorkerId(settlement.id),
                world.getCourierPhase(settlement.id));
        if (!envoyStatus.isEmpty()) {
            drawFittedText(canvas, envoyStatus, center, 291f, 14f, 320f, settlement.color, true);
        }
        if (!courierStatus.isEmpty()) {
            drawFittedText(canvas, courierStatus, center, 308f, 13f, 320f, settlement.color, true);
        }

        int requested = settlement.requestedResource;
        int offered = settlement.offeredResource;
        String exchange = String.format(text(R.string.trade_exchange),
                DiplomacySystem.TRADE_EXPORT_AMOUNT, resourceName(requested),
                DiplomacySystem.TRADE_IMPORT_AMOUNT, resourceName(offered));
        drawFittedText(canvas, exchange, center, 330f, 14f, 325f, 0xFF74694F, false);

        drawDiplomacyButton(canvas, center, 360f, text(R.string.send_envoy),
                world.getEnvoyTurns(settlement.id) == 0 && world.getIdleWorkerCount() > 0);
        drawDiplomacyButton(canvas, center, 410f, text(R.string.send_courier),
                world.getCourierTurns(settlement.id) == 0 && relation >= 15
                        && world.getIdleWorkerCount() > 0);
        String gift = text(R.string.give_gift) + " · "
                + String.format(text(R.string.gift_cost), DiplomacySystem.GIFT_AMOUNT, resourceName(requested));
        drawDiplomacyButton(canvas, center, 460f, gift,
                world.getResource(requested) >= DiplomacySystem.GIFT_AMOUNT);
        String trade = world.hasTradeRoute(settlement.id)
                ? text(R.string.trade_route_active) : text(R.string.open_trade_route);
        drawDiplomacyButton(canvas, center, 510f, trade,
                !world.hasTradeRoute(settlement.id)
                        && relation >= DiplomacySystem.TRADE_RELATION_REQUIRED);
        if (!world.hasTradeRoute(settlement.id)) {
            drawFittedText(canvas, text(R.string.trade_route_cost), center, 545f,
                    13f, 325f, 0xFF8A806E, false);
        }
        drawPill(canvas, center - 145f, 566f, center + 145f, 608f,
                0xFFE9DDCB, 0x183E3226);
        drawFittedText(canvas, text(R.string.back_to_kingdom), center, 593f,
                16f, 270f, 0xFF443C2E, true);
    }

    private void drawDiplomacyButton(Canvas canvas, float cx, float cy, String label, boolean enabled) {
        drawPill(canvas, cx - 165f, cy - 20f, cx + 165f, cy + 20f,
                enabled ? 0xFFE8D7AC : 0xFFE8E4D8, 0x183E3226);
        drawFittedText(canvas, label, cx, cy + 6f, 15f, 305f,
                enabled ? 0xFF443C2E : 0xFF9A9284, true);
    }

    private String missionStatus(int workerId, int phase) {
        if (workerId == BuildingType.NONE || phase == DiplomacySystem.MISSION_NONE) {
            return "";
        }
        int resource;
        if (phase == DiplomacySystem.MISSION_OUTBOUND) {
            resource = R.string.mission_outbound;
        } else if (phase == DiplomacySystem.MISSION_VISITING) {
            resource = R.string.mission_visiting;
        } else {
            resource = R.string.mission_returning;
        }
        return String.format(text(resource), workerId + 1);
    }

    private int relationStatusRes(int relation) {
        if (relation >= 70) {
            return R.string.relation_allied;
        }
        if (relation >= DiplomacySystem.TRADE_RELATION_REQUIRED) {
            return R.string.relation_friendly;
        }
        if (relation >= 15) {
            return R.string.relation_familiar;
        }
        return R.string.relation_wary;
    }

    private String resourceName(int resourceId) {
        return text(ResourceType.createAll()[resourceId].nameRes);
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private void drawPlacementBanner(Canvas canvas, float time) {
        BuildingType type = world.getBuildingTypes()[world.getPendingBuildingTypeId()];
        boolean showRejection = time < rejectionMessageUntil;
        drawPill(canvas, 350f, 656f, 930f, 660f + 44f, 0xF0FFFBEE, 0x203E3226);
        if (showRejection) {
            drawFittedText(canvas, rejectionMessage(), 640f, 685f, 20f, 550f, 0xFFB4553E, true);
        } else {
            drawFittedText(canvas, text(type.nameRes) + " - " + text(R.string.cancel) + "?",
                    640f, 685f, 20f, 550f, 0xFF443C2E, true);
        }
    }

    private String rejectionMessage() {
        switch (rejectionReason) {
            case KingdomWorld.PLACEMENT_REJECTED_TECH:
                return text(R.string.placement_needs_tech);
            case KingdomWorld.PLACEMENT_REJECTED_TERRAIN:
                BuildingType type = world.getBuildingTypes()[world.getPendingBuildingTypeId()];
                return String.format(text(R.string.placement_needs_terrain), text(terrainNameRes(type.requiredAdjacentTerrain)));
            case KingdomWorld.PLACEMENT_REJECTED_COST:
                return text(R.string.placement_needs_resources);
            case KingdomWorld.PLACEMENT_REJECTED_PROGRESSION:
                return text(R.string.placement_needs_progress);
            case KingdomWorld.PLACEMENT_REJECTED_UNBUILDABLE:
            default:
                return text(R.string.placement_needs_tile);
        }
    }

    private int terrainNameRes(int terrainId) {
        switch (terrainId) {
            case TerrainType.WATER:
                return R.string.terrain_water;
            case TerrainType.STONE_OUTCROP:
                return R.string.terrain_stone_outcrop;
            case TerrainType.FOREST:
            default:
                return R.string.terrain_forest;
        }
    }

    private void drawBuildMenu(Canvas canvas) {
        drawModalScrim(canvas);
        drawModalCard(canvas);
        drawFittedText(canvas, text(R.string.build_menu_title), (MODAL_LEFT + MODAL_RIGHT) / 2f, MODAL_TOP + 56f,
                34f, MODAL_RIGHT - MODAL_LEFT - 100f, 0xFF443C2E, true);
        BuildingType[] types = world.getBuildingTypes();
        int totalPages = buildMenuPageCount(types);
        buildMenuPage = Math.max(0, Math.min(totalPages - 1, buildMenuPage));
        drawFittedText(canvas, String.format(text(R.string.page_label), buildMenuPage + 1, totalPages)
                        + "  ·  " + text(R.string.swipe_hint),
                640f, MODAL_TOP + 84f, 14f, 620f, 0xFF827764, false);

        for (int slot = 0; slot < BUILDINGS_PER_PAGE; slot++) {
            BuildingType type = playerBuildableTypeAt(types,
                    buildMenuPage * BUILDINGS_PER_PAGE + slot);
            if (type == null) {
                break;
            }
            float left = 130f + (slot % 2) * 525f;
            float top = 172f + (slot / 2) * 205f;
            drawBuildingCard(canvas, type, left, top, 495f, 182f);
        }
        drawPageArrow(canvas, 142f, MODAL_BOTTOM - 40f, false, buildMenuPage > 0);
        drawPageArrow(canvas, 1138f, MODAL_BOTTOM - 40f, true,
                buildMenuPage + 1 < totalPages);
        drawPill(canvas, 550f, MODAL_BOTTOM - 62f, 730f, MODAL_BOTTOM - 18f,
                0xFFE9DDCB, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.cancel), (MODAL_LEFT + MODAL_RIGHT) / 2f, MODAL_BOTTOM - 32f,
                20f, 160f, 0xFF443C2E, true);
    }

    private void drawBuildingCard(Canvas canvas, BuildingType type, float left, float top,
            float width, float height) {
        boolean unlocked = world.isBuildingUnlocked(type.id);
        boolean affordable = world.canAffordBuilding(type.id);
        paint.setColor(0x20443C2E);
        canvas.drawRoundRect(left + 3f, top + 4f, left + width + 3f,
                top + height + 4f, 25f, 25f, paint);
        paint.setColor(affordable ? 0xFFFFFDF6 : unlocked ? 0xFFF4EFE3 : 0xFFE5E1D7);
        canvas.drawRoundRect(left, top, left + width, top + height, 25f, 25f, paint);
        paint.setColor(unlocked ? 0xFFF1E6CC : 0xFFD8D4CA);
        canvas.drawRoundRect(left + 16f, top + 18f, left + 136f, top + 138f, 22f, 22f, paint);
        Bitmap icon = sprites.buildingFor(type.id);
        spritePaint.setAlpha(unlocked ? 255 : 135);
        canvas.drawBitmap(icon, null, new RectF(left + 37f, top + 37f,
                left + 115f, top + 115f), spritePaint);
        spritePaint.setAlpha(255);
        drawFittedText(canvas, text(type.nameRes), left + 315f, top + 43f,
                22f, 325f, unlocked ? 0xFF443C2E : 0xFF8E8678, true);
        drawFittedText(canvas, formatBuildingEffect(type), left + 315f, top + 73f,
                14f, 325f, 0xFF736957, false);
        drawFittedText(canvas, formatCost(type), left + 315f, top + 104f,
                14f, 325f, affordable ? 0xFF5B714D : 0xFF9B7566, true);
        drawFittedText(canvas, formatBuildingRequirement(type), left + 315f, top + 135f,
                13f, 325f, unlocked ? 0xFF827764 : 0xFFA06F5E, false);
        drawFittedText(canvas, unlocked ? (affordable ? text(R.string.building_ready)
                        : text(R.string.placement_needs_resources)) : text(R.string.building_locked),
                left + 315f, top + 163f, 13f, 325f,
                affordable ? 0xFF55764B : 0xFF948878, true);
    }

    private String formatBuildingEffect(BuildingType type) {
        int output = world.getOutputResourceId(type.id);
        if (output != ResourceType.NONE) {
            return String.format(text(R.string.building_produces), type.outputPerTurn[output],
                    resourceName(output));
        }
        if (type.housing > 0) {
            return String.format(text(R.string.building_housing), type.housing);
        }
        if (type.techPointsPerTurn > 0) {
            return String.format(text(R.string.building_science), type.techPointsPerTurn);
        }
        if (type.storageCapBonus > 0) {
            return String.format(text(R.string.building_storage), type.storageCapBonus);
        }
        return String.format(text(R.string.building_growth),
                type.populationGrowthThresholdReduction);
    }

    private String formatBuildingRequirement(BuildingType type) {
        List<String> requirements = new ArrayList<>();
        if (type.requiredTechId != TechNode.NONE) {
            requirements.add(String.format(text(R.string.requires_technology),
                    text(world.getTechNodes()[type.requiredTechId].nameRes)));
        }
        if (type.minTurn > 0) {
            requirements.add(String.format(text(R.string.requires_turn), type.minTurn));
        }
        if (type.minPopulation > 0) {
            requirements.add(String.format(text(R.string.requires_population), type.minPopulation));
        }
        if (type.requiredBuildingTypeId != BuildingType.NONE) {
            requirements.add(String.format(text(R.string.requires_buildings),
                    type.requiredBuildingCount,
                    text(world.getBuildingTypes()[type.requiredBuildingTypeId].nameRes)));
        }
        return requirements.isEmpty() ? text(R.string.available_from_start)
                : android.text.TextUtils.join(" · ", requirements);
    }

    private void drawPageArrow(Canvas canvas, float cx, float cy, boolean right, boolean enabled) {
        paint.setColor(enabled ? 0xFFE8D7AC : 0xFFE4E0D6);
        canvas.drawCircle(cx, cy, 27f, paint);
        paint.setColor(enabled ? 0xFF443C2E : 0xFFAAA294);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float sign = right ? 1f : -1f;
        canvas.drawLine(cx - 7f * sign, cy - 10f, cx + 5f * sign, cy, paint);
        canvas.drawLine(cx + 5f * sign, cy, cx - 7f * sign, cy + 10f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private int countPlayerBuildableTypes(BuildingType[] types) {
        int count = 0;
        for (BuildingType type : types) {
            if (type.playerBuildable) {
                count++;
            }
        }
        return count;
    }

    private int buildMenuPageCount(BuildingType[] types) {
        return Math.max(1, (countPlayerBuildableTypes(types) + BUILDINGS_PER_PAGE - 1)
                / BUILDINGS_PER_PAGE);
    }

    private BuildingType playerBuildableTypeAt(BuildingType[] types, int requestedIndex) {
        int index = 0;
        for (int typeId = 1; typeId <= BuildingType.COUNT; typeId++) {
            BuildingType type = types[typeId % BuildingType.COUNT];
            if (!type.playerBuildable) {
                continue;
            }
            if (index == requestedIndex) {
                return type;
            }
            index++;
        }
        return null;
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

    private void drawBuildingPanel(Canvas canvas) {
        PlacedBuilding building = world.getBuilding(selectedBuildingId);
        if (building == null) {
            activeOverlay = Overlay.NONE;
            return;
        }
        BuildingType type = world.getBuildingTypes()[building.typeId];
        drawModalScrim(canvas);
        drawModalCard(canvas);
        drawFittedText(canvas, text(type.nameRes), 640f, 138f,
                34f, 900f, 0xFF443C2E, true);

        canvas.save();
        canvas.translate(350f, 315f);
        canvas.scale(2.15f, 2.15f);
        drawBuildingToken(canvas, type.id, 0f, 0f, !building.isComplete(), 0f);
        canvas.restore();

        float infoX = 735f;
        if (!building.isComplete()) {
            drawFittedText(canvas, text(R.string.construction_site), infoX, 238f,
                    25f, 600f, 0xFF6D5D45, true);
            drawFittedText(canvas, String.format(text(R.string.construction_steps_left),
                            building.turnsRemaining), infoX, 284f,
                    20f, 600f, 0xFF74694F, false);
            WorkerKitten builder = world.getConstructionWorker(building.id);
            String builderText = builder == null ? text(R.string.waiting_for_builder)
                    : String.format(text(R.string.kitten_number), builder.id + 1)
                    + " · " + workerStateText(builder);
            drawFittedText(canvas, builderText, infoX, 330f,
                    20f, 600f, builder == null ? 0xFFD07062 : 0xFF557A59, true);
        } else if (type.id == BuildingType.TOWN_HALL) {
            drawFittedText(canvas, text(R.string.workforce_heading), infoX, 238f,
                    25f, 600f, 0xFF6D5D45, true);
            drawFittedText(canvas, String.format(text(R.string.workforce_count),
                            world.getWorkerCount(), world.getPopulation()), infoX, 286f,
                    21f, 600f, 0xFF74694F, false);
            drawFittedText(canvas, String.format(text(R.string.hire_cost),
                            KingdomWorld.HIRE_FISH_COST, KingdomWorld.HIRE_CATNIP_COST), infoX, 330f,
                    18f, 600f, 0xFF74694F, false);
            drawActionButton(canvas, infoX, 430f, text(R.string.hire_kitten), world.canHireWorker());
        } else if (world.getOutputResourceId(type.id) != ResourceType.NONE) {
            int outputResource = world.getOutputResourceId(type.id);
            int output = type.outputPerTurn[outputResource];
            drawFittedText(canvas, text(R.string.production_heading), infoX, 226f,
                    25f, 600f, 0xFF6D5D45, true);
            drawResourceIcon(canvas, outputResource, infoX - 87f, 273f, 15f);
            drawFittedText(canvas, String.format(text(R.string.production_per_turn), output,
                            text(ResourceType.createAll()[outputResource].nameRes)), infoX + 30f, 280f,
                    20f, 430f, 0xFF74694F, false);
            String ready;
            if (building.hasReadyGoods()
                    && world.isStorageFullForResource(building.pendingResourceId)) {
                ready = String.format(text(R.string.goods_waiting_storage_full),
                        building.pendingAmount);
            } else if (building.hasReadyGoods()) {
                ready = String.format(text(R.string.goods_waiting), building.pendingAmount);
            } else {
                ready = text(R.string.no_goods_waiting);
            }
            drawFittedText(canvas, ready, infoX, 326f, 20f, 600f,
                    building.hasReadyGoods() ? 0xFF557A59 : 0xFF908777, true);
            WorkerKitten assigned = world.getAssignedWorker(building.id);
            String assignment = assigned == null ? text(R.string.no_worker_assigned)
                    : String.format(text(R.string.worker_assignment), assigned.id + 1,
                    workerStateText(assigned));
            drawFittedText(canvas, assignment, infoX, 372f, 19f, 600f,
                    assigned == null ? 0xFFD07062 : 0xFF557A59, true);
            drawActionButton(canvas, infoX, 452f,
                    assigned == null ? text(R.string.assign_kitten) : text(R.string.release_kitten), true);
        } else {
            drawFittedText(canvas, text(R.string.passive_building), infoX, 260f,
                    23f, 600f, 0xFF6D5D45, true);
            drawFittedText(canvas, text(R.string.no_worker_needed), infoX, 312f,
                    20f, 600f, 0xFF74694F, false);
        }

        drawPill(canvas, (MODAL_LEFT + MODAL_RIGHT) / 2f - 90f, MODAL_BOTTOM - 60f,
                (MODAL_LEFT + MODAL_RIGHT) / 2f + 90f, MODAL_BOTTOM - 20f, 0xFFE9DDCB, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.close_button), (MODAL_LEFT + MODAL_RIGHT) / 2f,
                MODAL_BOTTOM - 32f, 20f, 160f, 0xFF443C2E, true);
    }

    private void drawActionButton(Canvas canvas, float cx, float cy, String label, boolean enabled) {
        drawPill(canvas, cx - 155f, cy - 28f, cx + 155f, cy + 28f,
                enabled ? 0xFFC7E4C9 : 0xFFE5E1D7, 0x1E443C2E);
        drawFittedText(canvas, label, cx, cy + 7f, 20f, 280f,
                enabled ? 0xFF443C2E : 0xFF9A9284, true);
    }

    private String workerStateText(WorkerKitten worker) {
        switch (worker.state) {
            case WorkerKitten.TO_CONSTRUCTION:
            case WorkerKitten.TO_WORK:
                return text(R.string.worker_walking);
            case WorkerKitten.CONSTRUCTING:
                return text(R.string.worker_building);
            case WorkerKitten.WORKING:
                PlacedBuilding workplace = world.getBuilding(worker.assignedBuildingId);
                if (workplace != null && workplace.hasReadyGoods()
                        && world.isStorageFullForResource(workplace.pendingResourceId)) {
                    return text(R.string.worker_waiting_storage);
                }
                return text(R.string.worker_working);
            case WorkerKitten.COLLECTING:
                return text(R.string.worker_working);
            case WorkerKitten.TO_STORAGE:
                return text(R.string.worker_delivering);
            case WorkerKitten.DIPLOMACY:
                return text(R.string.worker_travelling);
            default:
                return text(R.string.worker_idle);
        }
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

    private void drawKittenAtOrigin(Canvas canvas, float time, int direction, boolean moving) {
        int frame = moving ? Math.floorMod((int) (time * 7f), 4) : 0;
        float idle = moving ? 0f : (float) Math.sin(time * 2f) * 0.7f;
        paint.setColor(0x35433B31);
        canvas.drawOval(-23f, 18f, 23f, 27f, paint);
        Bitmap sprite = kittenSprites.frame(direction, frame);
        canvas.drawBitmap(sprite, null, new RectF(-32f, -39f + idle, 32f, 25f + idle), spritePaint);
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

    private void spawnWildlife() {
        wildlifeCritters.clear();
        wildlifeRandom.setSeed(0xC0C04A11L);
        WorldMap map = world.getMap();
        spawnCritterGroup(map, WildlifeCritter.RABBIT, false);
        spawnCritterGroup(map, WildlifeCritter.HEDGEHOG, false);
        spawnCritterGroup(map, WildlifeCritter.BEE, false);
        spawnCritterGroup(map, WildlifeCritter.DUCKLING, true);
    }

    private void spawnCritterGroup(WorldMap map, int species, boolean requiresWater) {
        for (int i = 0; i < WILDLIFE_PER_SPECIES; i++) {
            int[] cell = findNearbyCell(map, WorldMap.START_ROW, WorldMap.START_COL,
                    WILDLIFE_VISIBLE_RADIUS, requiresWater, true, true);
            if (cell == null) {
                continue;
            }
            WildlifeCritter critter = new WildlifeCritter(species);
            critter.worldX = cell[1] * TILE + TILE / 2f;
            critter.worldY = cell[0] * TILE + TILE / 2f;
            critter.targetX = critter.worldX;
            critter.targetY = critter.worldY;
            critter.speed = 16f + wildlifeRandom.nextFloat() * 8f;
            critter.retargetIn = 1.5f + wildlifeRandom.nextFloat() * 2f;
            wildlifeCritters.add(critter);
        }
    }

    private int[] findNearbyCell(WorldMap map, int anchorRow, int anchorCol, int radius,
            boolean requiresWater, boolean exploredOnly, boolean avoidCritters) {
        List<int[]> candidates = new ArrayList<>();
        for (int row = anchorRow - radius; row <= anchorRow + radius; row++) {
            for (int col = anchorCol - radius; col <= anchorCol + radius; col++) {
                int dr = row - anchorRow;
                int dc = col - anchorCol;
                if (!map.inBounds(row, col) || dr * dr + dc * dc > radius * radius
                        || (exploredOnly && !map.isExplored(row, col))) {
                    continue;
                }
                boolean suitable = requiresWater
                        ? map.terrainAt(row, col) == TerrainType.WATER
                        : map.isWalkable(row, col);
                if (suitable && (!avoidCritters || isWildlifeCellFree(row, col))) {
                    candidates.add(new int[]{row, col});
                }
            }
        }
        return candidates.isEmpty() ? null
                : candidates.get(wildlifeRandom.nextInt(candidates.size()));
    }

    private boolean isWildlifeCellFree(int row, int col) {
        for (WildlifeCritter critter : wildlifeCritters) {
            float critterRow = critter.worldY / TILE;
            float critterCol = critter.worldX / TILE;
            if (Math.hypot(row + 0.5f - critterRow, col + 0.5f - critterCol) < 1.4f) {
                return false;
            }
        }
        return true;
    }

    private void updateWildlife(float dt) {
        WorldMap map = world.getMap();
        for (WildlifeCritter critter : wildlifeCritters) {
            critter.retargetIn -= dt;
            float dx = critter.targetX - critter.worldX;
            float dy = critter.targetY - critter.worldY;
            float distance = (float) Math.hypot(dx, dy);
            if (critter.retargetIn <= 0f || distance < 2f) {
                pickNewWildlifeTarget(map, critter);
                continue;
            }
            float step = critter.speed * dt;
            if (step >= distance) {
                critter.worldX = critter.targetX;
                critter.worldY = critter.targetY;
            } else {
                critter.worldX += dx / distance * step;
                critter.worldY += dy / distance * step;
            }
            if (Math.abs(dx) > 1f) {
                critter.facing = dx < 0f ? -1f : 1f;
            }
        }
    }

    private void pickNewWildlifeTarget(WorldMap map, WildlifeCritter critter) {
        int currentRow = (int) (critter.worldY / TILE);
        int currentCol = (int) (critter.worldX / TILE);
        boolean requiresWater = critter.species == WildlifeCritter.DUCKLING;
        int[] cell = findNearbyCell(map, currentRow, currentCol, 3,
                requiresWater, true, false);
        if (cell == null) {
            critter.retargetIn = 1f;
            return;
        }
        critter.targetX = cell[1] * TILE + TILE / 2f;
        critter.targetY = cell[0] * TILE + TILE / 2f;
        critter.retargetIn = 3f + wildlifeRandom.nextFloat() * 3f;
    }

    private void drawWildlifeCritter(Canvas canvas, WildlifeCritter critter, float time) {
        canvas.save();
        canvas.scale(critter.facing < 0f ? -1f : 1f, 1f);
        float bounceFreq = critter.species == WildlifeCritter.BEE ? 10f : 7f;
        float bounce = (float) Math.sin(time * bounceFreq + critter.worldX * 0.05f) * 1.6f;
        canvas.translate(0f, bounce);
        paint.setColor(0x24433B31);
        canvas.drawOval(-20f, 13f, 20f, 20f, paint);
        int frame = Math.floorMod((int) (time * 4f + critter.worldX * 0.02f), 2);
        Bitmap sprite = wildlifeSprites.frame(critter.species, frame);
        canvas.drawBitmap(sprite, null, new RectF(-27f, -30f, 27f, 24f), spritePaint);
        canvas.restore();
    }

    private void enqueueNotification(String text) {
        pendingNotifications.add(text);
    }

    private void updateNotifications(float dt) {
        if (currentNotificationText != null) {
            currentNotificationRemaining -= dt;
            if (currentNotificationRemaining <= 0f) {
                currentNotificationText = null;
            }
        }
        if (currentNotificationText == null && !pendingNotifications.isEmpty()) {
            currentNotificationText = pendingNotifications.poll();
            currentNotificationRemaining = NOTIFICATION_DURATION;
        }
    }

    private void drawNotificationBanner(Canvas canvas) {
        if (currentNotificationText == null) {
            return;
        }
        drawPill(canvas, 440f, 92f, 840f, 132f, 0xF0FFFBEE, 0x203E3226);
        drawFittedText(canvas, currentNotificationText, 640f, 118f, 19f, 360f, 0xFF443C2E, true);
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
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                overlayDownX = toLogicalX(event.getX());
                overlayDownY = toLogicalY(event.getY());
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float logicalX = toLogicalX(event.getX());
                float logicalY = toLogicalY(event.getY());
                if (activeOverlay == Overlay.BUILD_MENU
                        && Math.abs(logicalX - overlayDownX) >= OVERLAY_SWIPE_THRESHOLD
                        && Math.abs(logicalY - overlayDownY) < 150f) {
                    changeBuildMenuPage(logicalX < overlayDownX ? 1 : -1);
                } else {
                    handleOverlayTap(logicalX, logicalY);
                }
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
                spawnWildlife();
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
                spawnWildlife();
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
            spawnWildlife();
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
        if (activeOverlay == Overlay.WORLD_MAP) {
            handleWorldMapOverlayTap(logicalX, logicalY);
            invalidate();
            return;
        }
        float cancelCx = (MODAL_LEFT + MODAL_RIGHT) / 2f;
        float cancelCy = MODAL_BOTTOM - 40f;
        if (isInsidePill(logicalX, logicalY, cancelCx, cancelCy, 90f, 20f)) {
            activeOverlay = Overlay.NONE;
            invalidate();
            return;
        }
        if (activeOverlay == Overlay.BUILD_MENU) {
            handleBuildMenuTap(logicalX, logicalY);
        } else if (activeOverlay == Overlay.BUILDING_PANEL) {
            handleBuildingPanelTap(logicalX, logicalY);
        }
        invalidate();
    }

    private void handleBuildingPanelTap(float logicalX, float logicalY) {
        PlacedBuilding building = world.getBuilding(selectedBuildingId);
        if (building == null || !building.isComplete()) {
            return;
        }
        if (building.typeId == BuildingType.TOWN_HALL
                && isInsidePill(logicalX, logicalY, 735f, 430f, 155f, 28f)) {
            showWorkforceResult(world.hireWorker(), R.string.notify_worker_hired);
            return;
        }
        if (world.getOutputResourceId(building.typeId) != ResourceType.NONE
                && isInsidePill(logicalX, logicalY, 735f, 452f, 155f, 28f)) {
            WorkerKitten assigned = world.getAssignedWorker(building.id);
            int result = assigned == null
                    ? world.assignWorker(building.id) : world.unassignWorker(building.id);
            showWorkforceResult(result, assigned == null
                    ? R.string.notify_worker_assigned : R.string.notify_worker_released);
        }
    }

    private void showWorkforceResult(int result, int successMessageRes) {
        int messageRes;
        switch (result) {
            case KingdomWorld.WORKFORCE_OK:
                messageRes = successMessageRes;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                saveKingdom();
                break;
            case KingdomWorld.WORKFORCE_NO_KITTEN:
                messageRes = R.string.workforce_no_idle;
                performHapticFeedback(HapticFeedbackConstants.REJECT);
                break;
            case KingdomWorld.WORKFORCE_NEEDS_RESOURCES:
                messageRes = R.string.workforce_needs_resources;
                performHapticFeedback(HapticFeedbackConstants.REJECT);
                break;
            case KingdomWorld.WORKFORCE_POPULATION_LIMIT:
                messageRes = R.string.workforce_population_limit;
                performHapticFeedback(HapticFeedbackConstants.REJECT);
                break;
            default:
                return;
        }
        enqueueNotification(text(messageRes));
    }

    private void handleWorldMapOverlayTap(float logicalX, float logicalY) {
        if (isInsidePill(logicalX, logicalY, PAUSE_X, PAUSE_Y, 34f, 34f)) {
            world.pause();
            saveKingdom();
            return;
        }
        if (isInsidePill(logicalX, logicalY, WORLD_MAP_BTN_CX, BOTTOM_BTN_CY,
                BOTTOM_BTN_HALF_W, BOTTOM_BTN_HALF_H)) {
            activeOverlay = Overlay.NONE;
            return;
        }
        if (isInsidePill(logicalX, logicalY, BUILD_BTN_CX, BOTTOM_BTN_CY,
                BOTTOM_BTN_HALF_W, BOTTOM_BTN_HALF_H)) {
            activeOverlay = Overlay.BUILD_MENU;
            return;
        }
        if (isInsidePill(logicalX, logicalY, RESEARCH_BTN_CX, BOTTOM_BTN_CY,
                BOTTOM_BTN_HALF_W, BOTTOM_BTN_HALF_H)) {
            activeOverlay = Overlay.NONE;
            openTechTree();
            return;
        }
        if (isInsidePill(logicalX, logicalY, END_TURN_BTN_CX, BOTTOM_BTN_CY,
                BOTTOM_BTN_HALF_W, BOTTOM_BTN_HALF_H)) {
            world.endTurn();
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            announceForAccessibility(text(R.string.accessibility_turn_ended));
            saveKingdom();
            return;
        }

        for (Settlement settlement : world.getSettlements()) {
            if (Math.hypot(logicalX - settlement.mapX, logicalY - settlement.mapY) <= 66f) {
                selectedSettlementId = settlement.id;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                return;
            }
        }

        float panelCenter = (855f + 1238f) / 2f;
        if (isInsidePill(logicalX, logicalY, panelCenter, 590f, 145f, 22f)) {
            activeOverlay = Overlay.NONE;
            return;
        }
        if (isInsidePill(logicalX, logicalY, panelCenter, 360f, 165f, 20f)) {
            showDiplomacyResult(world.sendEnvoy(selectedSettlementId), R.string.diplomacy_started);
        } else if (isInsidePill(logicalX, logicalY, panelCenter, 410f, 165f, 20f)) {
            showDiplomacyResult(world.sendCourier(selectedSettlementId), R.string.diplomacy_started);
        } else if (isInsidePill(logicalX, logicalY, panelCenter, 460f, 165f, 20f)) {
            showDiplomacyResult(world.giveGift(selectedSettlementId), R.string.diplomacy_gift_sent);
        } else if (isInsidePill(logicalX, logicalY, panelCenter, 510f, 165f, 20f)) {
            showDiplomacyResult(world.establishTradeRoute(selectedSettlementId),
                    R.string.diplomacy_trade_opened);
        }
    }

    private void showDiplomacyResult(int result, int successMessageRes) {
        int messageRes;
        switch (result) {
            case DiplomacySystem.ACTION_OK:
                messageRes = successMessageRes;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                saveKingdom();
                break;
            case DiplomacySystem.ACTION_ALREADY_TRAVELLING:
                messageRes = R.string.diplomacy_already_travelling;
                performHapticFeedback(HapticFeedbackConstants.REJECT);
                break;
            case DiplomacySystem.ACTION_NEEDS_RELATION:
                messageRes = R.string.diplomacy_needs_relation;
                performHapticFeedback(HapticFeedbackConstants.REJECT);
                break;
            case DiplomacySystem.ACTION_NEEDS_RESOURCES:
                messageRes = R.string.diplomacy_needs_resources;
                performHapticFeedback(HapticFeedbackConstants.REJECT);
                break;
            case DiplomacySystem.ACTION_ROUTE_EXISTS:
                messageRes = R.string.diplomacy_route_exists;
                break;
            case DiplomacySystem.ACTION_NEEDS_WORKER:
                messageRes = R.string.diplomacy_needs_worker;
                performHapticFeedback(HapticFeedbackConstants.REJECT);
                break;
            case DiplomacySystem.ACTION_INVALID:
            default:
                return;
        }
        enqueueNotification(text(messageRes));
    }

    private void handleBuildMenuTap(float logicalX, float logicalY) {
        BuildingType[] types = world.getBuildingTypes();
        if (Math.hypot(logicalX - 142f, logicalY - (MODAL_BOTTOM - 40f)) <= 38f) {
            changeBuildMenuPage(-1);
            return;
        }
        if (Math.hypot(logicalX - 1138f, logicalY - (MODAL_BOTTOM - 40f)) <= 38f) {
            changeBuildMenuPage(1);
            return;
        }
        for (int slot = 0; slot < BUILDINGS_PER_PAGE; slot++) {
            BuildingType type = playerBuildableTypeAt(types,
                    buildMenuPage * BUILDINGS_PER_PAGE + slot);
            if (type == null) {
                break;
            }
            float left = 130f + (slot % 2) * 525f;
            float top = 172f + (slot / 2) * 205f;
            if (logicalX >= left && logicalX <= left + 495f
                    && logicalY >= top && logicalY <= top + 182f) {
                if (world.canAffordBuilding(type.id)) {
                    world.selectBuildingForPlacement(type.id);
                    activeOverlay = Overlay.NONE;
                    rejectionMessageUntil = 0f;
                }
                return;
            }
        }
    }

    private void changeBuildMenuPage(int delta) {
        int pageCount = buildMenuPageCount(world.getBuildingTypes());
        int next = Math.max(0, Math.min(pageCount - 1, buildMenuPage + delta));
        if (next == buildMenuPage) {
            return;
        }
        buildMenuPage = next;
        audio.playPageTurn();
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
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
        if (isInsidePill(logicalX, logicalY, WORLD_MAP_BTN_CX, BOTTOM_BTN_CY,
                BOTTOM_BTN_HALF_W, BOTTOM_BTN_HALF_H)) {
            world.cancelPlacement();
            activeOverlay = Overlay.WORLD_MAP;
            invalidate();
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
            rejectionMessageUntil = 0f;
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
        int pendingBuildingTypeId = world.getPendingBuildingTypeId();
        if (pendingBuildingTypeId == BuildingType.NONE) {
            PlacedBuilding building = world.getBuildingAt(row, col);
            if (building != null) {
                selectedBuildingId = building.id;
                activeOverlay = Overlay.BUILDING_PANEL;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
                return;
            }
        }
        if (world.tapCell(row, col)) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            rejectionMessageUntil = 0f;
        } else if (pendingBuildingTypeId != BuildingType.NONE) {
            rejectionReason = world.checkPlacement(pendingBuildingTypeId, row, col);
            rejectionMessageUntil = System.nanoTime() / 1_000_000_000f + 2.2f;
            performHapticFeedback(HapticFeedbackConstants.REJECT);
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
        intent.putExtra(TechTreeActivity.EXTRA_TURN, world.getTurn());
        intent.putExtra(TechTreeActivity.EXTRA_POPULATION, world.getPopulation());
        intent.putExtra(TechTreeActivity.EXTRA_RESOURCES, world.getResourceSnapshot());
        intent.putExtra(TechTreeActivity.EXTRA_BUILDING_COUNTS, world.getBuildingCountSnapshot());
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
        BuildingType type = world.getBuildingTypes()[buildingTypeId];
        enqueueNotification(String.format(text(R.string.notify_building_built), text(type.nameRes)));
    }

    @Override
    public void onTechUnlocked(int techId) {
        audio.playTechUnlocked();
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        announceForAccessibility(text(R.string.accessibility_tech_unlocked));
        TechNode tech = world.getTechNodes()[techId];
        enqueueNotification(String.format(text(R.string.notify_tech_unlocked), text(tech.nameRes)));
    }

    @Override
    public void onPopulationGrew(int newPopulation) {
        audio.playPopulationGrew();
    }

    @Override
    public void onDiplomacyEvent(int settlementId, int eventMask) {
        Settlement settlement = world.getSettlements()[settlementId];
        if ((eventMask & DiplomacySystem.EVENT_ENVOY_ARRIVED) != 0) {
            enqueueNotification(String.format(text(R.string.notify_envoy_arrived), text(settlement.nameRes)));
        }
        if ((eventMask & DiplomacySystem.EVENT_COURIER_ARRIVED) != 0) {
            enqueueNotification(String.format(text(R.string.notify_courier_arrived), text(settlement.nameRes)));
        }
        if ((eventMask & DiplomacySystem.EVENT_ENVOY_RETURNED) != 0) {
            enqueueNotification(String.format(text(R.string.notify_envoy_returned), text(settlement.nameRes)));
        }
        if ((eventMask & DiplomacySystem.EVENT_COURIER_RETURNED) != 0) {
            enqueueNotification(String.format(text(R.string.notify_courier_returned), text(settlement.nameRes)));
        }
    }

    @Override
    public void onGoodsReady(int buildingId, int resourceId, int amount) {
        // The floating resource bubble is the calm, persistent notification for ready goods.
    }

    @Override
    public void onGoodsDelivered(int workerId, int resourceId, int amount) {
        audio.playGoodsDelivered(resourceId);
        ResourceType resource = ResourceType.createAll()[resourceId];
        enqueueNotification(String.format(text(R.string.notify_goods_delivered),
                amount, text(resource.nameRes)));
        saveKingdom();
    }

    @Override
    public void onWorkerHired(int workerId) {
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
