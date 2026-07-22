/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** Large, swipeable research cards: four readable technologies per page. */
@SuppressLint("ViewConstructor")
final class TechTreeView extends View {
    interface Listener {
        void onTechSelected(int techId);
    }

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float CARD_LEFT = 40f;
    private static final float CARD_TOP = 25f;
    private static final float CARD_RIGHT = 1240f;
    private static final float CARD_BOTTOM = 695f;
    private static final int ITEMS_PER_PAGE = 4;
    private static final float SWIPE_THRESHOLD = 72f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint spritePaint = new Paint();
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final TechNode[] techNodes = TechNode.createAll();
    private final BuildingType[] buildingTypes = BuildingType.createAll();
    private final ResourceType[] resourceTypes = ResourceType.createAll();
    private final boolean[] techUnlocked;
    private final int[] resources;
    private final int[] buildingCounts;
    private final Listener listener;
    private final LinearGradient backgroundGradient;
    private final TerrainSprites sprites;
    private final AudioEngine audio = new AudioEngine();

    private Context localizedContext;
    private final int techPointPool;
    private final int turn;
    private final int population;
    private int activeTechId;
    private int page;
    private float downX;
    private float downY;
    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;

    TechTreeView(Context context, int techPointPool, int activeTechId, int unlockedBits,
            int turn, int population, int[] resources, int[] buildingCounts,
            String language, Listener listener) {
        super(context);
        this.listener = listener;
        this.techPointPool = techPointPool;
        this.activeTechId = activeTechId;
        this.turn = turn;
        this.population = population;
        this.resources = normalized(resources, ResourceType.COUNT);
        this.buildingCounts = normalized(buildingCounts, BuildingType.COUNT);
        this.techUnlocked = KingdomSerializer.unpackTechBits(unlockedBits);
        this.page = activeTechId == TechNode.NONE ? 0 : activeTechId / ITEMS_PER_PAGE;
        setFocusable(true);
        setClickable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        applyLanguage(language);
        android.graphics.Typeface font = context.getResources().getFont(R.font.nunito);
        regular = android.graphics.Typeface.create(font, android.graphics.Typeface.NORMAL);
        bold = android.graphics.Typeface.create(font, android.graphics.Typeface.BOLD);
        sprites = new TerrainSprites(context.getResources());
        setContentDescription(text(R.string.tech_tree_title));

        backgroundGradient = new LinearGradient(0f, 0f, 0f, WORLD_HEIGHT,
                new int[]{0xFFF7F0DE, 0xFFE4DDC7, 0xFFCBC2A6},
                new float[]{0f, 0.56f, 1f}, Shader.TileMode.CLAMP);
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
        paint.setShader(backgroundGradient);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);
        canvas.save();
        canvas.translate(viewOffsetX, viewOffsetY);
        canvas.scale(viewScale, viewScale);
        drawScreen(canvas);
        canvas.restore();
    }

    private void drawScreen(Canvas canvas) {
        drawPanel(canvas);
        drawFittedText(canvas, text(R.string.tech_tree_title) + "  ·  "
                        + text(R.string.tech_points_label) + " " + techPointPool,
                640f, 76f, 32f, 980f, 0xFF443C2E, true);
        drawFittedText(canvas, String.format(text(R.string.page_label), page + 1, pageCount())
                        + "  ·  " + text(R.string.swipe_hint),
                640f, 106f, 15f, 760f, 0xFF80745F, false);

        for (int slot = 0; slot < ITEMS_PER_PAGE; slot++) {
            int techId = page * ITEMS_PER_PAGE + slot;
            if (techId >= techNodes.length) {
                break;
            }
            int row = slot / 2;
            int col = slot % 2;
            float left = 85f + col * 565f;
            float top = 132f + row * 225f;
            drawTechCard(canvas, techNodes[techId], left, top, 545f, 205f);
        }

        drawArrow(canvas, 92f, 650f, false, page > 0);
        drawArrow(canvas, 1188f, 650f, true, page + 1 < pageCount());
        drawPill(canvas, 540f, 625f, 740f, 673f, 0xFFE9DDCB, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.back_button), 640f, 657f,
                20f, 180f, 0xFF443C2E, true);
    }

    private void drawTechCard(Canvas canvas, TechNode node, float left, float top,
            float width, float height) {
        boolean unlocked = techUnlocked[node.id];
        boolean available = !unlocked && TechNode.conditionsMet(node, techUnlocked, turn,
                population, resources, buildingCounts);
        boolean active = node.id == activeTechId;
        int fill = unlocked ? 0xFFE5F1D5 : active ? 0xFFFFE8B9
                : available ? 0xFFFFFDF6 : 0xFFE9E4D7;
        paint.setColor(0x24443C2E);
        canvas.drawRoundRect(left + 3f, top + 5f, left + width + 3f,
                top + height + 5f, 26f, 26f, paint);
        paint.setColor(fill);
        canvas.drawRoundRect(left, top, left + width, top + height, 26f, 26f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(active ? 4f : 1.5f);
        paint.setColor(active ? 0xFFE0A744 : available ? 0xAA776A55 : 0x44776A55);
        canvas.drawRoundRect(left, top, left + width, top + height, 26f, 26f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(unlocked ? 0xFFD0E5B8 : 0xFFF3EBD8);
        canvas.drawRoundRect(left + 18f, top + 18f, left + 128f, top + 128f, 22f, 22f, paint);
        Bitmap icon = sprites.buildingFor(node.iconBuildingId);
        canvas.drawBitmap(icon, null, new RectF(left + 34f, top + 31f,
                left + 112f, top + 109f), spritePaint);
        drawFittedText(canvas, String.valueOf(node.cost), left + 73f, top + 156f,
                19f, 85f, 0xFF5F5545, true);
        drawFittedText(canvas, text(R.string.tech_points_label), left + 73f, top + 179f,
                12f, 100f, 0xFF887D69, false);

        float textLeft = left + 150f;
        float textWidth = width - 170f;
        drawLeftFittedText(canvas, text(node.nameRes), textLeft, top + 42f,
                23f, textWidth, 0xFF443C2E, true);
        int statusColor = unlocked ? 0xFF4D7042 : active ? 0xFFA56C20
                : available ? 0xFF5A7850 : 0xFF8D8372;
        int statusRes = unlocked ? R.string.research_done : active ? R.string.research_active
                : available ? R.string.research_ready : R.string.research_locked;
        drawLeftFittedText(canvas, text(statusRes), textLeft, top + 68f,
                15f, textWidth, statusColor, true);
        drawLeftFittedText(canvas, effectText(node), textLeft, top + 98f,
                14f, textWidth, 0xFF6F6453, false);
        drawLeftFittedText(canvas, conditionText(node), textLeft, top + 128f,
                14f, textWidth, 0xFF6F6453, false);
        drawLeftFittedText(canvas, prerequisiteText(node), textLeft, top + 158f,
                13f, textWidth, 0xFF8A806E, false);
        if (!unlocked && !active) {
            drawPill(canvas, left + width - 153f, top + height - 39f,
                    left + width - 18f, top + height - 11f,
                    available ? 0xFFE8D7AC : 0xFFDCD7CC, 0x143E3226);
            drawFittedText(canvas, available ? text(R.string.research_ready)
                            : text(R.string.research_locked),
                    left + width - 85f, top + height - 20f, 12f, 120f,
                    available ? 0xFF443C2E : 0xFF918879, true);
        }
    }

    private String effectText(TechNode node) {
        StringBuilder unlockedBuildings = new StringBuilder();
        for (BuildingType building : buildingTypes) {
            if (building.requiredTechId != node.id) {
                continue;
            }
            if (unlockedBuildings.length() > 0) {
                unlockedBuildings.append(" / ");
            }
            unlockedBuildings.append(text(building.nameRes));
        }
        if (unlockedBuildings.length() > 0) {
            return String.format(text(R.string.tech_unlocks), unlockedBuildings);
        }
        if (node.yieldBonusResourceId != ResourceType.NONE) {
            return String.format(text(R.string.tech_yield_bonus), node.yieldBonusPercent,
                    text(resourceTypes[node.yieldBonusResourceId].nameRes));
        }
        return String.format(text(R.string.tech_growth_bonus),
                node.populationGrowthThresholdReduction);
    }

    private String conditionText(TechNode node) {
        StringBuilder result = new StringBuilder();
        append(result, node.minTurn > 0
                ? String.format(text(R.string.requires_turn), node.minTurn) : null);
        append(result, node.minPopulation > 0
                ? String.format(text(R.string.requires_population), node.minPopulation) : null);
        if (node.requiredBuildingTypeId != BuildingType.NONE) {
            append(result, String.format(text(R.string.requires_buildings), node.requiredBuildingCount,
                    text(buildingTypes[node.requiredBuildingTypeId].nameRes)));
        }
        if (node.requiredResourceId != ResourceType.NONE) {
            append(result, String.format(text(R.string.requires_resource), node.requiredResourceAmount,
                    text(resourceTypes[node.requiredResourceId].nameRes)));
        }
        return result.length() == 0 ? text(R.string.no_extra_requirements) : result.toString();
    }

    private String prerequisiteText(TechNode node) {
        if (node.prerequisites.length == 0) {
            return text(R.string.no_prerequisite_technology);
        }
        StringBuilder names = new StringBuilder();
        for (int prerequisite : node.prerequisites) {
            if (names.length() > 0) {
                names.append(" · ");
            }
            names.append(text(techNodes[prerequisite].nameRes));
        }
        return String.format(text(R.string.requires_technology), names);
    }

    private void drawPanel(Canvas canvas) {
        paint.setColor(0x264A4234);
        canvas.drawRoundRect(CARD_LEFT + 4f, CARD_TOP + 7f, CARD_RIGHT + 4f,
                CARD_BOTTOM + 7f, 40f, 40f, paint);
        paint.setColor(0xF8FDFAF0);
        canvas.drawRoundRect(CARD_LEFT, CARD_TOP, CARD_RIGHT, CARD_BOTTOM, 40f, 40f, paint);
    }

    private void drawArrow(Canvas canvas, float cx, float cy, boolean right, boolean enabled) {
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

    private void drawPill(Canvas canvas, float left, float top, float right, float bottom,
            int fillColor, int shadowColor) {
        float radius = (bottom - top) * 0.5f;
        paint.setColor(shadowColor);
        canvas.drawRoundRect(left + 3f, top + 4f, right + 3f, bottom + 4f, radius, radius, paint);
        paint.setColor(fillColor);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    private void drawFittedText(Canvas canvas, String value, float centerX, float baseline,
            float size, float maxWidth, int color, boolean useBold) {
        paint.setTextAlign(Paint.Align.CENTER);
        drawFitted(canvas, value, centerX, baseline, size, maxWidth, color, useBold);
    }

    private void drawLeftFittedText(Canvas canvas, String value, float left, float baseline,
            float size, float maxWidth, int color, boolean useBold) {
        paint.setTextAlign(Paint.Align.LEFT);
        drawFitted(canvas, value, left, baseline, size, maxWidth, color, useBold);
    }

    private void drawFitted(Canvas canvas, String value, float x, float baseline,
            float preferredSize, float maxWidth, int color, boolean useBold) {
        paint.setTypeface(useBold ? bold : regular);
        paint.setTextSize(preferredSize);
        float width = paint.measureText(value);
        if (width > maxWidth && width > 0f) {
            paint.setTextSize(Math.max(10f, preferredSize * maxWidth / width));
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(value, x, baseline, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (viewScale <= 0f) {
            return true;
        }
        float logicalX = (event.getX() - viewOffsetX) / viewScale;
        float logicalY = (event.getY() - viewOffsetY) / viewScale;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = logicalX;
            downY = logicalY;
            return true;
        }
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return true;
        }
        performClick();
        float deltaX = logicalX - downX;
        if (Math.abs(deltaX) >= SWIPE_THRESHOLD && Math.abs(logicalY - downY) < 150f) {
            changePage(deltaX < 0f ? 1 : -1);
            return true;
        }
        if (Math.hypot(logicalX - 92f, logicalY - 650f) <= 38f) {
            changePage(-1);
            return true;
        }
        if (Math.hypot(logicalX - 1188f, logicalY - 650f) <= 38f) {
            changePage(1);
            return true;
        }
        if (Math.abs(logicalX - 640f) <= 100f && Math.abs(logicalY - 649f) <= 25f) {
            ((Activity) getContext()).finish();
            return true;
        }
        for (int slot = 0; slot < ITEMS_PER_PAGE; slot++) {
            int techId = page * ITEMS_PER_PAGE + slot;
            if (techId >= techNodes.length) {
                break;
            }
            float left = 85f + (slot % 2) * 565f;
            float top = 132f + (slot / 2) * 225f;
            if (logicalX >= left && logicalX <= left + 545f
                    && logicalY >= top && logicalY <= top + 205f) {
                TechNode node = techNodes[techId];
                if (!techUnlocked[techId] && TechNode.conditionsMet(node, techUnlocked, turn,
                        population, resources, buildingCounts)) {
                    activeTechId = techId;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    listener.onTechSelected(techId);
                    invalidate();
                }
                return true;
            }
        }
        return true;
    }

    private void changePage(int delta) {
        int next = Math.max(0, Math.min(pageCount() - 1, page + delta));
        if (next == page) {
            return;
        }
        page = next;
        audio.playPageTurn();
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
    }

    private int pageCount() {
        return (techNodes.length + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
    }

    void close() {
        audio.close();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void applyLanguage(String languageCode) {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(languageCode));
        localizedContext = getContext().createConfigurationContext(configuration);
    }

    private String text(int resource) {
        return localizedContext.getString(resource);
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" · ");
        }
        builder.append(value);
    }

    private static int[] normalized(int[] source, int length) {
        int[] result = new int[length];
        if (source != null) {
            System.arraycopy(source, 0, result, 0, Math.min(source.length, length));
        }
        return result;
    }
}
