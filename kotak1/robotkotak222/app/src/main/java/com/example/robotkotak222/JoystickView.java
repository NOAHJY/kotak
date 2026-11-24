package com.example.robotkotak222;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class JoystickView extends View {

    public interface OnJoystickChangeListener {
        void onStateChanged(float x, float y);
    }

    private float centerX;
    private float centerY;
    private float baseRadius;
    private float knobRadius;
    private float knobX;
    private float knobY;

    private boolean isTouching = false;

    private final Paint paintBase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBaseBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintAxis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintKnobShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintKnob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintKnobBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintKnobCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintArrow = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path arrowPath = new Path();

    private OnJoystickChangeListener listener;

    public JoystickView(Context context) {
        super(context);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintAxis.setStyle(Paint.Style.STROKE);
        paintAxis.setStrokeWidth(3f);
        paintAxis.setColor(0x33FFFFFF);

        paintArrow.setStyle(Paint.Style.FILL);
        paintArrow.setColor(0x80FF4FA0);
    }

    public void setOnJoystickChangeListener(OnJoystickChangeListener newListener) {
        listener = newListener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        if (size == 0) size = 300;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) / 2f * 0.88f;
        knobRadius = baseRadius * 0.38f;
        knobX = centerX;
        knobY = centerY;
        setupShaders();
    }

    private void setupShaders() {
        if (baseRadius <= 0) return;

        // Base gradient (3D dish effect)
        RadialGradient baseGradient = new RadialGradient(
                centerX, centerY, baseRadius,
                new int[]{0xFF1C1D24, 0xFF121318, 0xFF282936},
                new float[]{0f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP
        );
        paintBase.setStyle(Paint.Style.FILL);
        paintBase.setShader(baseGradient);

        // Border gradient
        paintBaseBorder.setStyle(Paint.Style.STROKE);
        paintBaseBorder.setStrokeWidth(6f);
        LinearGradient borderGradient = new LinearGradient(
                centerX - baseRadius, centerY - baseRadius,
                centerX + baseRadius, centerY + baseRadius,
                0xFF55586B, 0xFF1A1B22,
                Shader.TileMode.CLAMP
        );
        paintBaseBorder.setShader(borderGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (centerX == 0 || centerY == 0) {
            centerX = getWidth() / 2f;
            centerY = getHeight() / 2f;
            baseRadius = Math.min(getWidth(), getHeight()) / 2f * 0.88f;
            knobRadius = baseRadius * 0.38f;
            if (!isTouching) {
                knobX = centerX;
                knobY = centerY;
            }
            setupShaders();
        }

        // Draw Base Circle
        canvas.drawCircle(centerX, centerY, baseRadius, paintBase);
        canvas.drawCircle(centerX, centerY, baseRadius, paintBaseBorder);

        // Draw Inner Ring
        paintAxis.setStrokeWidth(2f);
        paintAxis.setColor(0x22FFFFFF);
        canvas.drawCircle(centerX, centerY, baseRadius * 0.6f, paintAxis);

        // Draw Crosshair Lines
        paintAxis.setStrokeWidth(3f);
        paintAxis.setColor(0x33FFFFFF);
        canvas.drawLine(centerX - baseRadius * 0.85f, centerY, centerX + baseRadius * 0.85f, centerY, paintAxis);
        canvas.drawLine(centerX, centerY - baseRadius * 0.85f, centerX, centerY + baseRadius * 0.85f, paintAxis);

        // Draw Directional Arrows
        drawDirectionArrows(canvas);

        // Draw Knob Shadow
        paintKnobShadow.setStyle(Paint.Style.FILL);
        paintKnobShadow.setColor(0x66000000);
        float shadowOffsetX = (knobX - centerX) * 0.15f;
        float shadowOffsetY = (knobY - centerY) * 0.15f + 4f;
        canvas.drawCircle(knobX + shadowOffsetX, knobY + shadowOffsetY, knobRadius, paintKnobShadow);

        // Knob
        paintKnob.setStyle(Paint.Style.FILL);
        if (isTouching) {
            paintKnob.setShader(new RadialGradient(
                    knobX - knobRadius * 0.2f, knobY - knobRadius * 0.2f, knobRadius * 1.2f,
                    new int[]{0xFFFF65B2, 0xFFE91E63, 0xFF880E4F},
                    new float[]{0f, 0.7f, 1.0f},
                    Shader.TileMode.CLAMP
            ));
        } else {
            paintKnob.setShader(new RadialGradient(
                    knobX - knobRadius * 0.2f, knobY - knobRadius * 0.2f, knobRadius * 1.2f,
                    new int[]{0xFF4A4E69, 0xFF2B2D42, 0xFF161A23},
                    new float[]{0f, 0.7f, 1.0f},
                    Shader.TileMode.CLAMP
            ));
        }
        canvas.drawCircle(knobX, knobY, knobRadius, paintKnob);

        // Knob Border / Ring
        paintKnobBorder.setStyle(Paint.Style.STROKE);
        paintKnobBorder.setStrokeWidth(4f);
        paintKnobBorder.setColor(isTouching ? 0xFFFFFFFF : 0xFF8D99AE);
        canvas.drawCircle(knobX, knobY, knobRadius, paintKnobBorder);

        // Knob Center Grip Texture
        paintKnobCenter.setStyle(Paint.Style.FILL);
        paintKnobCenter.setColor(isTouching ? 0xFFFFFFFF : 0xFFD8E2DC);
        canvas.drawCircle(knobX, knobY, knobRadius * 0.3f, paintKnobCenter);
    }

    private void drawDirectionArrows(Canvas canvas) {
        float arrowOffset = baseRadius * 0.75f;
        float arrowSize = baseRadius * 0.08f;

        // Top arrow
        arrowPath.reset();
        arrowPath.moveTo(centerX, centerY - arrowOffset - arrowSize);
        arrowPath.lineTo(centerX - arrowSize, centerY - arrowOffset + arrowSize);
        arrowPath.lineTo(centerX + arrowSize, centerY - arrowOffset + arrowSize);
        arrowPath.close();
        canvas.drawPath(arrowPath, paintArrow);

        // Bottom arrow
        arrowPath.reset();
        arrowPath.moveTo(centerX, centerY + arrowOffset + arrowSize);
        arrowPath.lineTo(centerX - arrowSize, centerY + arrowOffset - arrowSize);
        arrowPath.lineTo(centerX + arrowSize, centerY + arrowOffset - arrowSize);
        arrowPath.close();
        canvas.drawPath(arrowPath, paintArrow);

        // Left arrow
        arrowPath.reset();
        arrowPath.moveTo(centerX - arrowOffset - arrowSize, centerY);
        arrowPath.lineTo(centerX - arrowOffset + arrowSize, centerY - arrowSize);
        arrowPath.lineTo(centerX - arrowOffset + arrowSize, centerY + arrowSize);
        arrowPath.close();
        canvas.drawPath(arrowPath, paintArrow);

        // Right arrow
        arrowPath.reset();
        arrowPath.moveTo(centerX + arrowOffset + arrowSize, centerY);
        arrowPath.lineTo(centerX + arrowOffset - arrowSize, centerY - arrowSize);
        arrowPath.lineTo(centerX + arrowOffset - arrowSize, centerY + arrowSize);
        arrowPath.close();
        canvas.drawPath(arrowPath, paintArrow);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                isTouching = true;
                updateKnob(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                isTouching = true;
                updateKnob(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                resetKnob();
                return true;
            case MotionEvent.ACTION_CANCEL:
                resetKnob();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateKnob(float touchX, float touchY) {
        float dx = touchX - centerX;
        float dy = touchY - centerY;
        float maxDist = baseRadius - knobRadius;
        if (maxDist <= 0) maxDist = 1f;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > maxDist) {
            dx = dx / dist * maxDist;
            dy = dy / dist * maxDist;
        }
        knobX = centerX + dx;
        knobY = centerY + dy;
        invalidate();
        if (listener != null) {
            listener.onStateChanged(dx / maxDist, -dy / maxDist);
        }
    }

    private void resetKnob() {
        isTouching = false;
        knobX = centerX;
        knobY = centerY;
        invalidate();
        if (listener != null) listener.onStateChanged(0f, 0f);
    }
}
