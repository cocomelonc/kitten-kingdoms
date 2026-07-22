/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** An offline, swipeable comic guide drawn with the same tiles and sprites as the game. */
@SuppressLint("ViewConstructor")
final class HelpView extends View {
    interface Listener {
        void onDone();
    }

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float CARD_LEFT = 54f;
    private static final float CARD_TOP = 38f;
    private static final float CARD_RIGHT = 1226f;
    private static final float CARD_BOTTOM = 682f;
    private static final float PAGE_LEFT = 82f;
    private static final float PAGE_TOP = 74f;
    private static final float PAGE_RIGHT = 1198f;
    private static final float PAGE_BOTTOM = 586f;
    private static final float PAGE_STRIDE = PAGE_RIGHT - PAGE_LEFT;
    private static final float NAV_Y = 632f;
    private static final float NAV_HALF_W = 106f;
    private static final float NAV_HALF_H = 25f;
    private static final float SWIPE_THRESHOLD = 90f;
    private static final int PAGE_COUNT = 8;

    private static final int[] TITLE_RES = {
            R.string.guide_explore_title,
            R.string.guide_first_buildings_title,
            R.string.guide_construction_title,
            R.string.guide_delivery_title,
            R.string.guide_balance_title,
            R.string.guide_hiring_title,
            R.string.guide_recovery_title,
            R.string.guide_expansion_title,
    };
    private static final int[] BODY_RES = {
            R.string.guide_explore_body,
            R.string.guide_first_buildings_body,
            R.string.guide_construction_body,
            R.string.guide_delivery_body,
            R.string.guide_balance_body,
            R.string.guide_hiring_body,
            R.string.guide_recovery_body,
            R.string.guide_expansion_body,
    };
    private static final int[] TIP_RES = {
            R.string.guide_explore_tip,
            R.string.guide_first_buildings_tip,
            R.string.guide_construction_tip,
            R.string.guide_delivery_tip,
            R.string.guide_balance_tip,
            R.string.guide_hiring_tip,
            R.string.guide_recovery_tip,
            R.string.guide_expansion_tip,
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint spritePaint = new Paint();
    private final Path path = new Path();
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final Listener listener;
    private final LinearGradient backgroundGradient;
    private final TerrainSprites sprites;
    private final KittenSprites kittenSprites;
    private final WorldMap guideMap = new WorldMap();

    private Context localizedContext;
    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;
    private float pagePosition;
    private float downPagePosition;
    private float downLogicalX;
    private float downLogicalY;
    private int targetPage;
    private boolean dragging;
    private boolean touching;
    private long lastFrameNanos;

    HelpView(Context context, String language, Listener listener) {
        super(context);
        this.listener = listener;
        setFocusable(true);
        setClickable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        applyLanguage(language);
        android.graphics.Typeface font = context.getResources().getFont(R.font.nunito);
        regular = android.graphics.Typeface.create(font, android.graphics.Typeface.NORMAL);
        bold = android.graphics.Typeface.create(font, android.graphics.Typeface.BOLD);
        sprites = new TerrainSprites(context.getResources());
        kittenSprites = new KittenSprites(context.getResources());
        spritePaint.setFilterBitmap(false);
        spritePaint.setAntiAlias(false);
        setContentDescription(text(R.string.guide_accessibility));

        backgroundGradient = new LinearGradient(
                0f, 0f, 0f, WORLD_HEIGHT,
                new int[]{0xFFF5EEDC, 0xFFE3DCC6, 0xFFCBC2A6},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP
        );
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
        float elapsed = lastFrameNanos == 0L ? 0f
                : Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        if (!touching) {
            float difference = targetPage - pagePosition;
            if (Math.abs(difference) < 0.002f) {
                pagePosition = targetPage;
            } else {
                pagePosition += difference * Math.min(1f, elapsed * 11f);
                postInvalidateOnAnimation();
            }
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(backgroundGradient);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        canvas.save();
        canvas.translate(viewOffsetX, viewOffsetY);
        canvas.scale(viewScale, viewScale);
        drawScreen(canvas, now / 1_000_000_000f);
        canvas.restore();
    }

    private void drawScreen(Canvas canvas, float time) {
        paint.setColor(0x264A4234);
        canvas.drawRoundRect(CARD_LEFT + 5f, CARD_TOP + 8f,
                CARD_RIGHT + 5f, CARD_BOTTOM + 8f, 42f, 42f, paint);
        paint.setColor(0xF8FDFAF0);
        canvas.drawRoundRect(CARD_LEFT, CARD_TOP, CARD_RIGHT, CARD_BOTTOM, 42f, 42f, paint);

        canvas.save();
        canvas.clipRect(PAGE_LEFT, PAGE_TOP, PAGE_RIGHT, PAGE_BOTTOM);
        int centerPage = Math.round(pagePosition);
        for (int page = Math.max(0, centerPage - 1); page <= Math.min(PAGE_COUNT - 1, centerPage + 1); page++) {
            canvas.save();
            canvas.translate((page - pagePosition) * PAGE_STRIDE, 0f);
            drawPage(canvas, page, time);
            canvas.restore();
        }
        canvas.restore();

        drawNavigation(canvas);
    }

    private void drawPage(Canvas canvas, int page, float time) {
        drawFittedText(canvas, text(TITLE_RES[page]), 640f, 124f,
                34f, 980f, 0xFF443C2E, true);
        drawFittedText(canvas, String.format(Locale.ROOT, "%d / %d", page + 1, PAGE_COUNT),
                1120f, 116f, 16f, 90f, 0xFF8D826E, true);

        switch (page) {
            case 0:
                drawExploreComic(canvas, time);
                break;
            case 1:
                drawFirstBuildingsComic(canvas, time);
                break;
            case 2:
                drawConstructionComic(canvas, time);
                break;
            case 3:
                drawDeliveryComic(canvas, time);
                break;
            case 4:
                drawBalanceComic(canvas, time);
                break;
            case 5:
                drawHiringComic(canvas, time);
                break;
            case 6:
                drawRecoveryComic(canvas, time);
                break;
            case 7:
            default:
                drawExpansionComic(canvas, time);
                break;
        }

        drawNarration(canvas, text(BODY_RES[page]), text(TIP_RES[page]));
    }

    private void drawExploreComic(Canvas canvas, float time) {
        drawComicFrame(canvas, 112f, 160f, 698f, 512f, 0xFFE8E0C8);
        float tile = 64f;
        int baseRow = WorldMap.START_ROW - 2;
        int baseCol = WorldMap.START_COL - 4;
        canvas.save();
        canvas.clipRect(126f, 174f, 684f, 498f);
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                int mapRow = baseRow + row;
                int mapCol = baseCol + col;
                Bitmap tileBitmap = sprites.terrainFor(guideMap, mapRow, mapCol,
                        1701 + row * 31 + col * 17);
                canvas.drawBitmap(tileBitmap, null,
                        new RectF(126f + col * tile, 174f + row * tile,
                                126f + (col + 1) * tile, 174f + (row + 1) * tile), spritePaint);
                if (col >= 7) {
                    paint.setColor(col == 7 ? 0x7A5F6670 : 0xC85F6670);
                    canvas.drawRect(126f + col * tile, 174f + row * tile,
                            126f + (col + 1) * tile, 174f + (row + 1) * tile, paint);
                }
            }
        }
        drawKitten(canvas, 450f, 337f, KittenSprites.RIGHT, true, time, 1.1f);
        drawArrow(canvas, 488f, 337f, 590f, 337f, 0xFFF7F2E4);
        canvas.restore();
        drawSpeech(canvas, 175f, 190f, 385f, 238f, text(R.string.guide_tap_to_walk));
    }

    private void drawFirstBuildingsComic(Canvas canvas, float time) {
        drawComicFrame(canvas, 112f, 160f, 698f, 512f, 0xFFDDE8C7);
        drawGroundGrid(canvas, 132f, 184f, 8, 5);
        drawBuilding(canvas, BuildingType.TOWN_HALL, 222f, 340f, 1.35f, 255);
        drawBuilding(canvas, BuildingType.FISHING_DOCK, 423f, 268f, 1.2f, 255);
        drawBuilding(canvas, BuildingType.CATNIP_FARM, 570f, 404f, 1.2f, 255);
        drawKitten(canvas, 330f, 284f, KittenSprites.RIGHT, true, time, 0.86f);
        drawKitten(canvas, 460f, 408f, KittenSprites.RIGHT, true, time + 0.4f, 0.86f);
        drawNumberBadge(canvas, 404f, 206f, 1, 0xFF6FB0D8);
        drawNumberBadge(canvas, 600f, 346f, 2, 0xFF8FC15C);
        drawSpeech(canvas, 154f, 188f, 350f, 232f, text(R.string.guide_one_dock_one_farm));
    }

    private void drawConstructionComic(Canvas canvas, float time) {
        drawComicFrame(canvas, 112f, 160f, 698f, 512f, 0xFFE9DFC0);
        drawGroundGrid(canvas, 132f, 184f, 8, 5);
        drawBuilding(canvas, BuildingType.CATNIP_FARM, 522f, 342f, 1.35f, 125);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(0xFFF7EBC5);
        canvas.drawCircle(522f, 332f, 45f, paint);
        paint.setStyle(Paint.Style.FILL);
        drawKitten(canvas, 332f, 342f, KittenSprites.RIGHT, true, time, 1f);
        drawArrow(canvas, 375f, 342f, 462f, 342f, 0xFF7B6B51);
        drawHammer(canvas, 520f, 266f, 1.2f);
        drawSpeech(canvas, 154f, 188f, 390f, 236f, text(R.string.guide_builder_walks));
    }

    private void drawDeliveryComic(Canvas canvas, float time) {
        float[] left = {110f, 308f, 506f};
        for (int panel = 0; panel < 3; panel++) {
            drawComicFrame(canvas, left[panel], 178f, left[panel] + 176f, 490f,
                    panel == 1 ? 0xFFE7D8E2 : 0xFFDCE7C7);
            drawGroundGrid(canvas, left[panel] + 10f, 248f, 2, 3);
            drawNumberBadge(canvas, left[panel] + 24f, 198f, panel + 1,
                    panel == 0 ? 0xFF6FB0D8 : panel == 1 ? 0xFFD88FB0 : 0xFFE9B65C);
        }
        drawBuilding(canvas, BuildingType.FISHING_DOCK, 198f, 355f, 1.15f, 255);
        drawResourceBubble(canvas, ResourceType.FISH, 4, 198f, 272f);
        drawKitten(canvas, 396f, 354f, KittenSprites.RIGHT, true, time, 0.95f);
        drawResourceBubble(canvas, ResourceType.FISH, 4, 396f, 286f);
        drawBuilding(canvas, BuildingType.TOWN_HALL, 594f, 356f, 1.2f, 255);
        drawArrow(canvas, 270f, 334f, 316f, 334f, 0xFF7B6B51);
        drawArrow(canvas, 468f, 334f, 514f, 334f, 0xFF7B6B51);
        drawFittedText(canvas, text(R.string.guide_ready), 198f, 465f, 17f, 145f, 0xFF5C523F, true);
        drawFittedText(canvas, text(R.string.guide_carry), 396f, 465f, 17f, 145f, 0xFF5C523F, true);
        drawFittedText(canvas, text(R.string.guide_store), 594f, 465f, 17f, 145f, 0xFF5C523F, true);
    }

    private void drawBalanceComic(Canvas canvas, float time) {
        drawComicFrame(canvas, 112f, 160f, 698f, 512f, 0xFFE8E0C8);
        drawBuilding(canvas, BuildingType.FISHING_DOCK, 250f, 305f, 1.3f, 255);
        drawResourceBubble(canvas, ResourceType.FISH, 4, 250f, 218f);
        drawBuilding(canvas, BuildingType.CATNIP_FARM, 550f, 305f, 1.3f, 255);
        drawResourceBubble(canvas, ResourceType.CATNIP, 4, 550f, 218f);
        drawKitten(canvas, 247f, 410f, KittenSprites.DOWN, false, time, 0.85f);
        drawKitten(canvas, 547f, 410f, KittenSprites.DOWN, false, time + 0.5f, 0.85f);
        paint.setColor(0xFF6FB0D8);
        canvas.drawCircle(334f, 456f, 11f, paint);
        paint.setColor(0xFF8FC15C);
        canvas.drawCircle(368f, 456f, 11f, paint);
        drawFittedText(canvas, text(R.string.guide_growth_formula), 470f, 464f,
                18f, 390f, 0xFF5C523F, true);
    }

    private void drawHiringComic(Canvas canvas, float time) {
        drawComicFrame(canvas, 112f, 160f, 698f, 512f, 0xFFE1D7C0);
        drawBuilding(canvas, BuildingType.TOWN_HALL, 278f, 330f, 1.75f, 255);
        drawKitten(canvas, 454f, 263f, KittenSprites.DOWN, false, time, 0.8f);
        drawKitten(canvas, 520f, 328f, KittenSprites.DOWN, false, time + 0.3f, 0.8f);
        drawKitten(canvas, 454f, 397f, KittenSprites.DOWN, false, time + 0.6f, 0.8f);
        drawPill(canvas, 394f, 438f, 642f, 486f, 0xFFC7E4C9, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.hire_kitten), 518f, 469f,
                19f, 220f, 0xFF443C2E, true);
        drawResourceIcon(canvas, ResourceType.FISH, 435f, 205f, 14f);
        drawFittedText(canvas, "5", 463f, 212f, 20f, 34f, 0xFF443C2E, true);
        drawResourceIcon(canvas, ResourceType.CATNIP, 512f, 205f, 14f);
        drawFittedText(canvas, "2", 540f, 212f, 20f, 34f, 0xFF443C2E, true);
    }

    private void drawRecoveryComic(Canvas canvas, float time) {
        drawComicFrame(canvas, 112f, 160f, 698f, 512f, 0xFFE8DAD2);
        drawBuilding(canvas, BuildingType.FISHING_DOCK, 226f, 290f, 1.15f, 255);
        drawBuilding(canvas, BuildingType.FISHING_DOCK, 430f, 290f, 1.15f, 255);
        drawKitten(canvas, 226f, 398f, KittenSprites.DOWN, false, time, 0.78f);
        drawKitten(canvas, 430f, 398f, KittenSprites.DOWN, false, time + 0.3f, 0.78f);
        drawPill(canvas, 142f, 444f, 344f, 484f, 0xFFF0C9C2, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.release_kitten), 243f, 470f,
                16f, 178f, 0xFF5B443D, true);
        drawArrow(canvas, 468f, 402f, 548f, 402f, 0xFF7B6B51);
        drawBuilding(canvas, BuildingType.CATNIP_FARM, 610f, 401f, 1.05f, 130);
        drawHammer(canvas, 612f, 332f, 0.9f);
        drawSpeech(canvas, 142f, 184f, 648f, 226f, text(R.string.guide_not_stuck));
    }

    private void drawExpansionComic(Canvas canvas, float time) {
        drawComicFrame(canvas, 112f, 160f, 698f, 512f, 0xFFDDE6CC);
        int[] buildingIds = {
                BuildingType.LUMBER_CAMP, BuildingType.KITTEN_COTTAGE,
                BuildingType.STORAGE_BARN, BuildingType.SCHOLARS_DEN,
                BuildingType.WEAVERS_COTTAGE, BuildingType.CRYSTAL_MINE,
        };
        for (int index = 0; index < buildingIds.length; index++) {
            int col = index % 3;
            int row = index / 3;
            float cx = 212f + col * 190f;
            float cy = 278f + row * 158f;
            paint.setColor(0xBFFFF9EA);
            canvas.drawRoundRect(cx - 70f, cy - 73f, cx + 70f, cy + 60f, 24f, 24f, paint);
            drawBuilding(canvas, buildingIds[index], cx, cy, 1.02f, 255);
        }
        drawFittedText(canvas, text(R.string.guide_route), 405f, 492f,
                17f, 520f, 0xFF5C523F, true);
    }

    private void drawNarration(Canvas canvas, String body, String tip) {
        paint.setColor(0xFFF4EBD7);
        canvas.drawRoundRect(730f, 166f, 1168f, 496f, 30f, 30f, paint);
        drawWrappedText(canvas, body, 768f, 208f, 362f,
                20f, 28f, 0xFF554B3A, false, 7);
        paint.setColor(0xFFDCE9CE);
        canvas.drawRoundRect(758f, 402f, 1140f, 474f, 20f, 20f, paint);
        drawWrappedText(canvas, tip, 782f, 432f, 334f,
                17f, 23f, 0xFF506348, true, 2);
    }

    private void drawNavigation(Canvas canvas) {
        if (targetPage > 0 || pagePosition > 0.15f) {
            drawPill(canvas, 114f, NAV_Y - NAV_HALF_H, 114f + NAV_HALF_W * 2f,
                    NAV_Y + NAV_HALF_H, 0xFFE9DDCB, 0x1E443C2E);
            drawFittedText(canvas, text(R.string.back_button), 114f + NAV_HALF_W,
                    NAV_Y + 7f, 19f, 180f, 0xFF443C2E, true);
        }

        String nextLabel = targetPage == PAGE_COUNT - 1
                ? text(R.string.got_it) : text(R.string.next_button);
        int nextFill = targetPage == PAGE_COUNT - 1 ? 0xFFC7E4C9 : 0xFFEFE3C4;
        drawPill(canvas, 1052f - NAV_HALF_W, NAV_Y - NAV_HALF_H,
                1052f + NAV_HALF_W, NAV_Y + NAV_HALF_H, nextFill, 0x1E443C2E);
        drawFittedText(canvas, nextLabel, 1052f, NAV_Y + 7f,
                19f, 180f, 0xFF443C2E, true);

        float dotsWidth = (PAGE_COUNT - 1) * 24f;
        float firstX = 640f - dotsWidth / 2f;
        for (int page = 0; page < PAGE_COUNT; page++) {
            float distance = Math.min(1f, Math.abs(pagePosition - page));
            paint.setColor(blendColor(0xFFB8AD96, 0xFF7E9B69, 1f - distance));
            canvas.drawCircle(firstX + page * 24f, NAV_Y, 5f + (1f - distance) * 2f, paint);
        }
    }

    private void drawComicFrame(Canvas canvas, float left, float top, float right, float bottom, int fill) {
        paint.setColor(0x1F443C2E);
        canvas.drawRoundRect(left + 3f, top + 5f, right + 3f, bottom + 5f, 28f, 28f, paint);
        paint.setColor(fill);
        canvas.drawRoundRect(left, top, right, bottom, 28f, 28f, paint);
    }

    private void drawGroundGrid(Canvas canvas, float left, float top, int columns, int rows) {
        float tile = 64f;
        int baseRow = WorldMap.START_ROW - rows / 2;
        int baseCol = WorldMap.START_COL - columns / 2;
        canvas.save();
        canvas.clipRect(left, top, left + columns * tile, top + rows * tile);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                Bitmap tileBitmap = sprites.terrainFor(guideMap, baseRow + row, baseCol + col,
                        3181 + row * 41 + col * 19);
                canvas.drawBitmap(tileBitmap, left + col * tile, top + row * tile, spritePaint);
            }
        }
        canvas.restore();
    }

    private void drawBuilding(Canvas canvas, int typeId, float cx, float cy, float scale, int alpha) {
        Bitmap sprite = sprites.buildingFor(typeId);
        float size = 72f * scale;
        paint.setColor(0x30433B31);
        canvas.drawOval(cx - size * 0.35f, cy + size * 0.28f,
                cx + size * 0.35f, cy + size * 0.42f, paint);
        spritePaint.setAlpha(alpha);
        canvas.drawBitmap(sprite, null, new RectF(cx - size / 2f, cy - size / 2f,
                cx + size / 2f, cy + size / 2f), spritePaint);
        spritePaint.setAlpha(255);
    }

    private void drawKitten(Canvas canvas, float cx, float cy, int direction,
                            boolean moving, float time, float scale) {
        int frame = moving ? Math.floorMod((int) (time * 7f), 4) : 0;
        Bitmap sprite = kittenSprites.frame(direction, frame);
        paint.setColor(0x30433B31);
        canvas.drawOval(cx - 20f * scale, cy + 17f * scale,
                cx + 20f * scale, cy + 25f * scale, paint);
        canvas.drawBitmap(sprite, null, new RectF(cx - 31f * scale, cy - 38f * scale,
                cx + 31f * scale, cy + 24f * scale), spritePaint);
    }

    private void drawResourceBubble(Canvas canvas, int resourceId, int amount, float cx, float cy) {
        paint.setColor(0xF4FFF9E9);
        canvas.drawRoundRect(cx - 34f, cy - 20f, cx + 34f, cy + 20f, 20f, 20f, paint);
        drawResourceIcon(canvas, resourceId, cx - 12f, cy, 13f);
        drawFittedText(canvas, String.valueOf(amount), cx + 17f, cy + 6f,
                16f, 25f, 0xFF443C2E, true);
    }

    private void drawResourceIcon(Canvas canvas, int resourceId, float cx, float cy, float size) {
        if (resourceId == ResourceType.FISH) {
            paint.setColor(0xFF6FB0D8);
            canvas.drawOval(cx - size * 0.72f, cy - size * 0.38f,
                    cx + size * 0.42f, cy + size * 0.38f, paint);
            path.reset();
            path.moveTo(cx + size * 0.32f, cy);
            path.lineTo(cx + size * 0.9f, cy - size * 0.48f);
            path.lineTo(cx + size * 0.9f, cy + size * 0.48f);
            path.close();
            canvas.drawPath(path, paint);
        } else if (resourceId == ResourceType.CATNIP) {
            paint.setColor(0xFF8FC15C);
            canvas.save();
            canvas.rotate(-30f, cx, cy);
            canvas.drawOval(cx - size * 0.65f, cy - size * 0.22f,
                    cx + size * 0.1f, cy + size * 0.32f, paint);
            canvas.drawOval(cx - size * 0.05f, cy - size * 0.42f,
                    cx + size * 0.72f, cy + size * 0.12f, paint);
            canvas.restore();
        } else {
            paint.setColor(ResourceType.createAll()[resourceId].color);
            canvas.drawCircle(cx, cy, size * 0.7f, paint);
        }
    }

    private void drawHammer(Canvas canvas, float cx, float cy, float scale) {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(6f * scale);
        paint.setColor(0xFF9B7550);
        canvas.drawLine(cx - 8f * scale, cy + 10f * scale,
                cx + 5f * scale, cy - 8f * scale, paint);
        paint.setStrokeWidth(10f * scale);
        paint.setColor(0xFFA8A2AC);
        canvas.drawLine(cx - 2f * scale, cy - 12f * scale,
                cx + 12f * scale, cy - 2f * scale, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawArrow(Canvas canvas, float fromX, float fromY, float toX, float toY, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(5f);
        paint.setColor(color);
        canvas.drawLine(fromX, fromY, toX, toY, paint);
        float angle = (float) Math.atan2(toY - fromY, toX - fromX);
        float wing = 13f;
        canvas.drawLine(toX, toY,
                toX - (float) Math.cos(angle - 0.6f) * wing,
                toY - (float) Math.sin(angle - 0.6f) * wing, paint);
        canvas.drawLine(toX, toY,
                toX - (float) Math.cos(angle + 0.6f) * wing,
                toY - (float) Math.sin(angle + 0.6f) * wing, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawNumberBadge(Canvas canvas, float cx, float cy, int number, int color) {
        paint.setColor(color);
        canvas.drawCircle(cx, cy, 18f, paint);
        drawFittedText(canvas, String.valueOf(number), cx, cy + 7f,
                18f, 24f, 0xFFFFFFFF, true);
    }

    private void drawSpeech(Canvas canvas, float left, float top, float right, float bottom, String value) {
        paint.setColor(0xF5FFF9EC);
        canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, paint);
        drawFittedText(canvas, value, (left + right) / 2f, (top + bottom) / 2f + 6f,
                16f, right - left - 20f, 0xFF554B3A, true);
    }

    private void drawPill(Canvas canvas, float left, float top, float right, float bottom,
                          int fillColor, int shadowColor) {
        float radius = (bottom - top) * 0.5f;
        paint.setColor(shadowColor);
        canvas.drawRoundRect(left + 3f, top + 4f, right + 3f, bottom + 4f, radius, radius, paint);
        paint.setColor(fillColor);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
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

    private void drawWrappedText(Canvas canvas, String value, float left, float top, float maxWidth,
                                 float textSize, float lineHeight, int color, boolean useBold,
                                 int maxLines) {
        paint.setTypeface(useBold ? bold : regular);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(textSize);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        String[] words = value.split(" ");
        StringBuilder line = new StringBuilder();
        int lineIndex = 0;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            canvas.drawText(line.toString(), left, top + lineIndex * lineHeight, paint);
            lineIndex++;
            if (lineIndex >= maxLines) {
                return;
            }
            line.setLength(0);
            line.append(word);
        }
        if (line.length() > 0 && lineIndex < maxLines) {
            canvas.drawText(line.toString(), left, top + lineIndex * lineHeight, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (viewScale <= 0f) {
            return true;
        }
        float logicalX = (event.getX() - viewOffsetX) / viewScale;
        float logicalY = (event.getY() - viewOffsetY) / viewScale;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = true;
                dragging = false;
                downLogicalX = logicalX;
                downLogicalY = logicalY;
                downPagePosition = pagePosition;
                targetPage = clampPage(Math.round(pagePosition));
                return true;
            case MotionEvent.ACTION_MOVE:
                float deltaX = logicalX - downLogicalX;
                float deltaY = logicalY - downLogicalY;
                if (!dragging && Math.abs(deltaX) > 12f && Math.abs(deltaX) > Math.abs(deltaY)) {
                    dragging = true;
                }
                if (dragging) {
                    pagePosition = clampFloat(downPagePosition - deltaX / PAGE_STRIDE,
                            0f, PAGE_COUNT - 1f);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                targetPage = clampPage(Math.round(pagePosition));
                postInvalidateOnAnimation();
                return true;
            case MotionEvent.ACTION_UP:
                touching = false;
                performClick();
                float swipe = logicalX - downLogicalX;
                if (dragging) {
                    if (swipe <= -SWIPE_THRESHOLD) {
                        targetPage = clampPage((int) Math.floor(downPagePosition) + 1);
                    } else if (swipe >= SWIPE_THRESHOLD) {
                        targetPage = clampPage((int) Math.ceil(downPagePosition) - 1);
                    } else {
                        targetPage = clampPage(Math.round(pagePosition));
                    }
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } else {
                    handleTap(logicalX, logicalY);
                }
                postInvalidateOnAnimation();
                return true;
            default:
                return true;
        }
    }

    private void handleTap(float logicalX, float logicalY) {
        if (Math.abs(logicalY - NAV_Y) > NAV_HALF_H + 8f) {
            return;
        }
        if (logicalX >= 114f && logicalX <= 114f + NAV_HALF_W * 2f && targetPage > 0) {
            targetPage--;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            return;
        }
        if (Math.abs(logicalX - 1052f) <= NAV_HALF_W) {
            if (targetPage == PAGE_COUNT - 1) {
                listener.onDone();
            } else {
                targetPage++;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int clampPage(int page) {
        return Math.max(0, Math.min(PAGE_COUNT - 1, page));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int blendColor(int from, int to, float amount) {
        float safe = clampFloat(amount, 0f, 1f);
        int red = Math.round(((from >> 16) & 0xFF) * (1f - safe) + ((to >> 16) & 0xFF) * safe);
        int green = Math.round(((from >> 8) & 0xFF) * (1f - safe) + ((to >> 8) & 0xFF) * safe);
        int blue = Math.round((from & 0xFF) * (1f - safe) + (to & 0xFF) * safe);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private void applyLanguage(String languageCode) {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(languageCode));
        localizedContext = getContext().createConfigurationContext(configuration);
    }

    private String text(int resource) {
        return localizedContext.getString(resource);
    }
}
