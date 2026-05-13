package cn.modificator.launcher.widgets;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * 将通知角标绘制到应用图标上。
 * <p>
 * 高对比黑白风格：右上角实心黑色圆 + 居中白色数字（"9+" 表示溢出）。
 * 直接生成 {@link BitmapDrawable}，调用方负责不要污染 IconCache。
 */
public final class IconBadge {

  /** 角标圆形直径，相对于图标短边的比例（约 1/3）。 */
  private static final float BADGE_SIZE_RATIO = 0.36f;
  /** 数字文字高度，相对于角标直径的比例。 */
  private static final float TEXT_SIZE_RATIO = 0.7f;
  /** 最小角标像素尺寸，避免 1×1 图标场景下绘制不可见。 */
  private static final int MIN_BADGE_PX = 18;
  /** 角标白色边框宽度，相对于角标直径的比例。 */
  private static final float STROKE_RATIO = 0.10f;

  private IconBadge() {
  }

  /**
   * 在 base 之上绘制角标，返回新的 Drawable。
   *
   * @param resources 用于构造 BitmapDrawable，以便 ImageView 正确处理密度
   * @param base      原始图标；null 时返回 null
   * @param count     通知数量；&lt;= 0 时直接返回 base
   * @return 装饰后的新 Drawable；不修改 base
   */
  public static Drawable decorate(Resources resources, Drawable base, int count) {
    if (base == null) return null;
    if (count <= 0) return base;

    int width = base.getIntrinsicWidth();
    int height = base.getIntrinsicHeight();
    if (width <= 0 || height <= 0) {
      // adaptive / vector 等无固有尺寸的图标：回退到常见 48dp 像素值
      width = Math.max(width, 48);
      height = Math.max(height, 48);
    }

    Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    // 1) 画原图标
    Rect oldBounds = base.copyBounds();
    base.setBounds(0, 0, width, height);
    base.draw(canvas);
    base.setBounds(oldBounds);

    // 2) 角标几何参数
    int shortSide = Math.min(width, height);
    int badgeSize = Math.max((int) (shortSide * BADGE_SIZE_RATIO), MIN_BADGE_PX);
    float cx = width - badgeSize / 2f;
    float cy = badgeSize / 2f;
    float radius = badgeSize / 2f;

    // 3) 白色描边（让黑圆从深色背景里"挤"出来，墨水屏上对比更稳）
    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setColor(Color.WHITE);
    stroke.setStyle(Paint.Style.FILL);
    canvas.drawCircle(cx, cy, radius, stroke);

    // 4) 黑色实心圆
    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setColor(Color.BLACK);
    fill.setStyle(Paint.Style.FILL);
    float innerRadius = radius - radius * STROKE_RATIO;
    canvas.drawCircle(cx, cy, innerRadius, fill);

    // 5) 数字 / "9+"
    String text = count > 9 ? "9+" : Integer.toString(count);
    Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setColor(Color.WHITE);
    textPaint.setTextAlign(Paint.Align.CENTER);
    textPaint.setFakeBoldText(true);
    float textSize = innerRadius * 2 * TEXT_SIZE_RATIO;
    // "9+" 占两位，需要进一步缩小避免越界
    if (text.length() > 1) textSize *= 0.75f;
    textPaint.setTextSize(textSize);
    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float textY = cy - (fm.ascent + fm.descent) / 2f;
    canvas.drawText(text, cx, textY, textPaint);

    return new BitmapDrawable(resources, bmp);
  }
}
