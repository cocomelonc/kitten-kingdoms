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
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * A single static "how to play" card: four short sections, no
 * pagination or scrolling since each section is intentionally kept to two short lines.
 */
@SuppressLint("ViewConstructor")
final class HelpView extends View {
    interface Listener {
        void onDone();
    }

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float CARD_LEFT = 90f;
    private static final float CARD_TOP = 60f;
    private static final float CARD_RIGHT = 1190f;
    private static final float CARD_BOTTOM = 660f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final Listener listener;
    private final LinearGradient backgroundGradient;

    private Context localizedContext;
    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;

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
        setContentDescription(text(R.string.how_to_play));

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
        paint.setColor(0x264A4234);
        canvas.drawRoundRect(CARD_LEFT + 4f, CARD_TOP + 7f, CARD_RIGHT + 4f, CARD_BOTTOM + 7f, 40f, 40f, paint);
        paint.setColor(0xF8FDFAF0);
        canvas.drawRoundRect(CARD_LEFT, CARD_TOP, CARD_RIGHT, CARD_BOTTOM, 40f, 40f, paint);

        drawFittedText(canvas, text(R.string.how_to_play), (CARD_LEFT + CARD_RIGHT) / 2f, CARD_TOP + 66f,
                38f, CARD_RIGHT - CARD_LEFT - 120f, 0xFF443C2E, true);

        float columnWidth = (CARD_RIGHT - CARD_LEFT) / 4f;
        drawSection(canvas, 0, CARD_LEFT + columnWidth * 0.5f,
                R.string.help_explore_heading, R.string.help_explore_line1, R.string.help_explore_line2);
        drawSection(canvas, 1, CARD_LEFT + columnWidth * 1.5f,
                R.string.help_build_heading, R.string.help_build_line1, R.string.help_build_line2);
        drawSection(canvas, 2, CARD_LEFT + columnWidth * 2.5f,
                R.string.help_research_heading, R.string.help_research_line1, R.string.help_research_line2);
        drawSection(canvas, 3, CARD_LEFT + columnWidth * 3.5f,
                R.string.help_diplomacy_heading, R.string.help_diplomacy_line1, R.string.help_diplomacy_line2);

        drawPill(canvas, (CARD_LEFT + CARD_RIGHT) / 2f - 100f, CARD_BOTTOM - 76f,
                (CARD_LEFT + CARD_RIGHT) / 2f + 100f, CARD_BOTTOM - 32f, 0xFFE9DDCB, 0x1E443C2E);
        drawFittedText(canvas, text(R.string.got_it), (CARD_LEFT + CARD_RIGHT) / 2f, CARD_BOTTOM - 46f,
                21f, 180f, 0xFF443C2E, true);
    }

    private void drawSection(Canvas canvas, int glyph, float cx, int headingRes, int line1Res, int line2Res) {
        float iconCy = CARD_TOP + 160f;
        paint.setColor(0xFFEFE3C4);
        canvas.drawCircle(cx, iconCy, 40f, paint);
        drawSectionGlyph(canvas, glyph, cx, iconCy);

        float columnWidth = (CARD_RIGHT - CARD_LEFT) / 4f - 32f;
        drawFittedText(canvas, text(headingRes), cx, iconCy + 76f, 24f, columnWidth, 0xFF443C2E, true);
        drawFittedText(canvas, text(line1Res), cx, iconCy + 116f, 17f, columnWidth, 0xFF6B6250, false);
        drawFittedText(canvas, text(line2Res), cx, iconCy + 142f, 17f, columnWidth, 0xFF6B6250, false);
    }

    private void drawSectionGlyph(Canvas canvas, int glyph, float cx, float cy) {
        paint.setColor(0xFF443C2E);
        switch (glyph) {
            case 0:
                canvas.drawCircle(cx, cy + 6f, 12f, paint);
                canvas.drawCircle(cx - 13f, cy - 9f, 6f, paint);
                canvas.drawCircle(cx, cy - 16f, 6f, paint);
                canvas.drawCircle(cx + 13f, cy - 9f, 6f, paint);
                break;
            case 1:
                path.reset();
                path.moveTo(cx - 17f, cy + 15f);
                path.lineTo(cx, cy - 17f);
                path.lineTo(cx + 17f, cy + 15f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case 2:
                canvas.drawCircle(cx, cy - 4f, 13f, paint);
                canvas.drawRoundRect(cx - 5f, cy + 8f, cx + 5f, cy + 17f, 2f, 2f, paint);
                break;
            case 3:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4f);
                canvas.drawLine(cx - 18f, cy + 10f, cx, cy - 12f, paint);
                canvas.drawLine(cx, cy - 12f, cx + 18f, cy + 10f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - 18f, cy + 10f, 7f, paint);
                canvas.drawCircle(cx, cy - 12f, 7f, paint);
                canvas.drawCircle(cx + 18f, cy + 10f, 7f, paint);
                break;
            default:
                break;
        }
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
        listener.onDone();
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
