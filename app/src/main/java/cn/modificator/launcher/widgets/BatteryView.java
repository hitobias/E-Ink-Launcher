package cn.modificator.launcher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * 圆形电量指示 View。
 * 外圈弧线表示当前电量百分比，中心显示数字。
 */
public class BatteryView extends View {

  // anti-alias disabled for e-ink: EPD renders pure black/white pixels
  private final Paint circlePaint = new Paint();
  private final TextPaint textPaint = new TextPaint();
  private final RectF arcRect = new RectF();
  private final Rect textBounds = new Rect();

  private int maxProgress = 100;
  private int progress = 0;

  public BatteryView(Context context) {
    super(context);
    init();
  }

  public BatteryView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public BatteryView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    circlePaint.setStyle(Paint.Style.STROKE);
    textPaint.setColor(0xff000000);
    textPaint.setFakeBoldText(true);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int size = Math.min(getWidth(), getHeight());
    float strokeWidth = size / 10f;
    circlePaint.setStrokeWidth(strokeWidth);

    // 画灰色背景圆环
    circlePaint.setColor(0xffcccccc);
    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, (size - strokeWidth) / 2f, circlePaint);

    // 画黑色电量弧线
    arcRect.set(
        (getWidth() - size + strokeWidth) / 2f,
        (getHeight() - size + strokeWidth) / 2f,
        (getWidth() - size - strokeWidth) / 2f + size,
        (getHeight() - size - strokeWidth) / 2f + size
    );
    circlePaint.setColor(0xff000000);
    float sweepAngle = progress * 1f / maxProgress * 360;
    canvas.drawArc(arcRect, -90, sweepAngle, false, circlePaint);

    // 画中心文字
    textPaint.setTextSize(size / 2.8f);
    drawText(canvas);
  }

  private void drawText(Canvas canvas) {
    String showText = String.format(Locale.US, "%02d", Math.round(progress * 1f / maxProgress * 100));
    textPaint.getTextBounds(showText, 0, showText.length(), textBounds);
    canvas.translate(getWidth() / 2f, getHeight() / 2f);
    canvas.drawText(showText,
        -(textBounds.right - textBounds.left) / 1.9f,
        (textBounds.bottom - textBounds.top) / 2f, textPaint);
  }

  public void setMaxProgress(int maxProgress) {
    if (this.maxProgress == maxProgress) return;
    this.maxProgress = maxProgress;
    invalidate();
  }

  public void setProgress(int progress) {
    if (this.progress == progress) return;
    this.progress = progress;
    invalidate();
  }
}
