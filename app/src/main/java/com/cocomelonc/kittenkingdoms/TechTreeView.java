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
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * Full-screen research/technology tree: a standalone view for {@link TechTreeActivity}.
 * Holds its own small copy of the tech point pool, active selection, and unlocked set, all
 * supplied by the host Activity via Intent extras rather than a live {@link KingdomWorld}
 * reference (the two Activities are separate processes-of-navigation, not sharing objects).
 */
@SuppressLint("ViewConstructor")
final class TechTreeView extends View {
    interface Listener {
        void onTechSelected(int techId);
    }

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float CARD_LEFT = 40f;
    private static final float CARD_TOP = 30f;
    private static final float CARD_RIGHT = 1240f;
    private static final float CARD_BOTTOM = 690f;
    private static final int[] TECH_DEPTH = {0, 1, 1, 1, 1, 2, 2, 3, 4, 5};
    private static final int[] TECH_ROW_INDEX = {0, 0, 1, 2, 3, 0, 1, 0, 0, 0};
    private static final int[] TECH_ROW_COUNT = {1, 4, 2, 1, 1, 1};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final TechNode[] techNodes = TechNode.createAll();
    private final boolean[] techUnlocked;
    private final Listener listener;
    private final LinearGradient backgroundGradient;

    private Context localizedContext;
    private int techPointPool;
    private int activeTechId;
    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;

    TechTreeView(Context context, int techPointPool, int activeTechId, int unlockedBits,
                 String language, Listener listener) {
        super(context);
        this.listener = listener;
        this.techPointPool = techPointPool;
        this.activeTechId = activeTechId;
        this.techUnlocked = KingdomSerializer.unpackTechBits(unlockedBits);
        setFocusable(true);
        setClickable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        applyLanguage(language);
        android.graphics.Typeface font = context.getResources().getFont(R.font.nunito);
        regular = android.graphics.Typeface.create(font, android.graphics.Typeface.NORMAL);
        bold = android.graphics.Typeface.create(font, android.graphics.Typeface.BOLD);
        setContentDescription(text(R.string.tech_tree_title));

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
        paint.setStyle(Paint.Style.FILL);
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
        drawCard(canvas);
        drawFittedText(canvas, text(R.string.tech_tree_title) + "  ·  " + text(R.string.tech_points_label)
                        + " " + techPointPool,
                (CARD_LEFT + CARD_RIGHT) / 2f, CARD_TOP + 62f,
                34f, CARD_RIGHT - CARD_LEFT - 120f, 0xFF443C2E, true);

        float contentLeft = CARD_LEFT + 110f;
        float contentTop = CARD_TOP + 120f;
        float contentWidth = CARD_RIGHT - CARD_LEFT - 220f;
        float contentHeight = CARD_BOTTOM - CARD_TOP - 220f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f);
        paint.setColor(0x553E5C6B);
        float edgeWaypointX = contentLeft + contentWidth + 36f;
        for (TechNode node : techNodes) {
            float nodeX = techNodeX(node.id, contentLeft, contentWidth);
            float nodeY = techNodeY(node.id, contentTop, contentHeight);
            for (int prereq : node.prerequisites) {
                float prereqX = techNodeX(prereq, contentLeft, contentWidth);
                float prereqY = techNodeY(prereq, contentTop, contentHeight);
                int depthGap = Math.abs(TECH_DEPTH[node.id] - TECH_DEPTH[prereq]);
                if (depthGap > 1) {
                    path.reset();
                    path.moveTo(prereqX, prereqY);
                    path.lineTo(edgeWaypointX, prereqY);
                    path.lineTo(edgeWaypointX, nodeY);
                    path.lineTo(nodeX, nodeY);
                    canvas.drawPath(path, paint);
                } else {
                    canvas.drawLine(prereqX, prereqY, nodeX, nodeY, paint);
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);

        for (TechNode node : techNodes) {
            float nodeX = techNodeX(node.id, contentLeft, contentWidth);
            float nodeY = techNodeY(node.id, contentTop, contentHeight);
            boolean unlocked = techUnlocked[node.id];
            boolean available = !unlocked && TechNode.prerequisitesMet(node, techUnlocked);
            boolean active = node.id == activeTechId;
            int fill = unlocked ? 0xFF8FC15C : (active ? 0xFFE9B65C : (available ? 0xFFFFFDF6 : 0xFFDCD6C6));
            paint.setColor(fill);
            canvas.drawCircle(nodeX, nodeY, 34f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(active ? 3.8f : 1.8f);
            paint.setColor(available || active || unlocked ? 0xFF443C2E : 0x66443C2E);
            canvas.drawCircle(nodeX, nodeY, 34f, paint);
            paint.setStyle(Paint.Style.FILL);
            drawFittedText(canvas, text(node.nameRes), nodeX, nodeY + 52f, 15f,
                    contentWidth / TECH_ROW_COUNT[TECH_DEPTH[node.id]] - 8f, 0xFF443C2E, active);
            drawFittedText(canvas, String.valueOf(node.cost), nodeX, nodeY + 6f, 16f, 44f,
                    unlocked ? 0xFFFFFFFF : 0xFF443C2E, true);
        }

        drawPill(canvas, (CARD_LEFT + CARD_RIGHT) / 2f - 100f, CARD_BOTTOM - 66f,
                (CARD_LEFT + CARD_RIGHT) / 2f + 100f, CARD_BOTTOM - 22f, 0xFFE9DDCB, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.back_button), (CARD_LEFT + CARD_RIGHT) / 2f, CARD_BOTTOM - 36f,
                20f, 180f, 0xFF443C2E, true);
    }

    private void drawCard(Canvas canvas) {
        paint.setColor(0x264A4234);
        canvas.drawRoundRect(CARD_LEFT + 4f, CARD_TOP + 7f, CARD_RIGHT + 4f, CARD_BOTTOM + 7f, 40f, 40f, paint);
        paint.setColor(0xF8FDFAF0);
        canvas.drawRoundRect(CARD_LEFT, CARD_TOP, CARD_RIGHT, CARD_BOTTOM, 40f, 40f, paint);
    }

    private float techNodeX(int techId, float left, float width) {
        int row = TECH_DEPTH[techId];
        int index = TECH_ROW_INDEX[techId];
        int count = TECH_ROW_COUNT[row];
        return left + (index + 0.5f) / count * width;
    }

    private float techNodeY(int techId, float top, float height) {
        int row = TECH_DEPTH[techId];
        return top + (row + 0.5f) / TECH_ROW_COUNT.length * height;
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (viewScale <= 0f || event.getActionMasked() != MotionEvent.ACTION_UP) {
            return true;
        }
        performClick();
        float logicalX = (event.getX() - viewOffsetX) / viewScale;
        float logicalY = (event.getY() - viewOffsetY) / viewScale;

        float backCx = (CARD_LEFT + CARD_RIGHT) / 2f;
        float backCy = CARD_BOTTOM - 44f;
        if (Math.abs(logicalX - backCx) <= 100f && Math.abs(logicalY - backCy) <= 22f) {
            ((android.app.Activity) getContext()).finish();
            return true;
        }

        float contentLeft = CARD_LEFT + 110f;
        float contentTop = CARD_TOP + 120f;
        float contentWidth = CARD_RIGHT - CARD_LEFT - 220f;
        float contentHeight = CARD_BOTTOM - CARD_TOP - 220f;
        for (TechNode node : techNodes) {
            float nodeX = techNodeX(node.id, contentLeft, contentWidth);
            float nodeY = techNodeY(node.id, contentTop, contentHeight);
            if (Math.hypot(logicalX - nodeX, logicalY - nodeY) <= 34f) {
                if (!techUnlocked[node.id] && TechNode.prerequisitesMet(node, techUnlocked)) {
                    activeTechId = node.id;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    listener.onTechSelected(activeTechId);
                    invalidate();
                }
                return true;
            }
        }
        return true;
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
}
