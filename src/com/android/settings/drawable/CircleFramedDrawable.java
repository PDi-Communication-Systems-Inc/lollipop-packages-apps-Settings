/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.drawable;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.Random;

import com.android.settings.R;

/**
 * Converts the user avatar icon to a circularly clipped one.
 * TODO: Move this to an internal framework class and share with the one in Keyguard.
 */
public class CircleFramedDrawable extends Drawable {

    private final Bitmap mBitmap;
    private final int mSize;
    private final Paint mPaint;
    private final float mShadowRadius;
    private final float mStrokeWidth;
    private final int mFrameColor;
    private final int mHighlightColor;
    private final int mFrameShadowColor;

    private float mScale;
    private Path mFramePath;
    private Rect mSrcRect;
    private RectF mDstRect;
    private RectF mFrameRect;
    private boolean mPressed;

    public static CircleFramedDrawable getInstance(Context context, Bitmap icon) {
        Resources res = context.getResources();
        float iconSize = res.getDimension(R.dimen.circle_avatar_size);
        float strokeWidth = res.getDimension(R.dimen.circle_avatar_frame_stroke_width);
        float shadowRadius = res.getDimension(R.dimen.circle_avatar_frame_shadow_radius);
        int frameColor = res.getColor(R.color.circle_avatar_frame_color);
        int frameShadowColor = res.getColor(R.color.circle_avatar_frame_shadow_color);
        int highlightColor = res.getColor(R.color.circle_avatar_frame_pressed_color);

        CircleFramedDrawable instance = new CircleFramedDrawable(icon,
                (int) iconSize, frameColor, strokeWidth, frameShadowColor, shadowRadius,
                highlightColor);
        return instance;
    }

    public CircleFramedDrawable(Bitmap icon, int size,
            int frameColor, float strokeWidth,
            int frameShadowColor, float shadowRadius,
            int highlightColor) {
        super();
        mSize = size;
        mShadowRadius = shadowRadius;
        mFrameColor = frameColor;
        mFrameShadowColor = frameShadowColor;
        mStrokeWidth = strokeWidth;
        mHighlightColor = highlightColor;

        mBitmap = Bitmap.createBitmap(mSize, mSize, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(mBitmap);

	// mrobbeloth PDi if the icon ever is null, it's a technicolor world
	if (icon == null) {
	   icon = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
	   Random gen = new Random(System.currentTimeMillis());
	   for (int i = 0; i < 100; i++) {
	       for (int j = 0; j < 100; j++) {
		   icon.setPixel(i, j, gen.nextInt(256));
	       }
	   }	
	}

        final int width = icon.getWidth();
        final int height = icon.getHeight();
        final int square = Math.min(width, height);

        final Rect cropRect = new Rect((width - square) / 2, (height - square) / 2, square, square);
        final RectF circleRect = new RectF(0f, 0f, mSize, mSize);
        circleRect.inset(mStrokeWidth / 2f, mStrokeWidth / 2f);
        circleRect.inset(mShadowRadius, mShadowRadius);

        final Path fillPath = new Path();
        fillPath.addArc(circleRect, 0f, 360f);

        canvas.drawColor(0, PorterDuff.Mode.CLEAR);

        // opaque circle matte
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(fillPath, mPaint);

        // mask in the icon where the bitmap is opaque
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(icon, cropRect, circleRect, mPaint);

        // prepare paint for frame drawing
        mPaint.setXfermode(null);

        mScale = 1f;

        mSrcRect = new Rect(0, 0, mSize, mSize);
        mDstRect = new RectF(0, 0, mSize, mSize);
        mFrameRect = new RectF(mDstRect);
        mFramePath = new Path();
    }

    @Override
    public void draw(Canvas canvas) {
        final float inside = mScale * mSize;
        final float pad = (mSize - inside) / 2f;

        mDstRect.set(pad, pad, mSize - pad, mSize - pad);
        canvas.drawBitmap(mBitmap, mSrcRect, mDstRect, null);

        mFrameRect.set(mDstRect);
        mFrameRect.inset(mStrokeWidth / 2f, mStrokeWidth / 2f);
        mFrameRect.inset(mShadowRadius, mShadowRadius);

        mFramePath.reset();
        mFramePath.addArc(mFrameRect, 0f, 360f);

        // white frame
        if (mPressed) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(Color.argb((int) (0.33f * 255),
                            Color.red(mHighlightColor),
                            Color.green(mHighlightColor),
                            Color.blue(mHighlightColor)));
            canvas.drawPath(mFramePath, mPaint);
        }
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(mPressed ? mHighlightColor : mFrameColor);
        mPaint.setShadowLayer(mShadowRadius, 0f, 0f, mFrameShadowColor);
        canvas.drawPath(mFramePath, mPaint);
    }

    public void setScale(float scale) {
        mScale = scale;
    }

    public float getScale() {
        return mScale;
    }

    public void setPressed(boolean pressed) {
        mPressed = pressed;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }
    
    @Override
    public int getIntrinsicWidth() {
        return mSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return mSize;
    }
}
