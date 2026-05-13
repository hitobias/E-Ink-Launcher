package cn.modificator.launcher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 分页指示点：横向 N 个圆点，当前页填充，其余空心。墨水屏友好，仅在 set 改变时 invalidate。
 */
public class PageIndicator extends View {

  private final Paint paint = new Paint();
  private int total = 0;
  private int current = 0;

  public PageIndicator(Context context) {
    super(context);
    paint.setColor(0xff000000);
  }

  public PageIndicator(Context context, AttributeSet attrs) {
    super(context, attrs);
    paint.setColor(0xff000000);
  }

  public PageIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    paint.setColor(0xff000000);
  }

  public void set(int total, int current) {
    if (total < 0) total = 0;
    if (current < 0) current = 0;
    if (current >= total && total > 0) current = total - 1;
    if (this.total == total && this.current == current) return;
    this.total = total;
    this.current = current;
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (total <= 1) return;
    int h = getHeight();
    int cy = h / 2;
    float radius = Math.min(h / 4f, 5f * getResources().getDisplayMetrics().density);
    float gap = radius * 2.5f;
    float totalWidth = total * (radius * 2) + (total - 1) * (gap - radius * 2);
    float startX = (getWidth() - totalWidth) / 2f + radius;
    for (int i = 0; i < total; i++) {
      float cx = startX + i * gap;
      if (i == current) {
        paint.setStyle(Paint.Style.FILL);
      } else {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, radius / 3f));
      }
      canvas.drawCircle(cx, cy, radius, paint);
    }
  }
}
