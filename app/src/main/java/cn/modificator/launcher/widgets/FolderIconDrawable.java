package cn.modificator.launcher.widgets;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件夹合成图标：白底带细黑边的方形容器，里面 2x2 排布最多 4 个迷你 app 图标。
 * <p>
 * 设计注意：
 * <ul>
 *   <li>墨水屏：不开抗锯齿（{@link Paint#setAntiAlias} 默认 false），边线保持锐利。</li>
 *   <li>没有迷你图标时画一个空白容器，避免崩溃；有 1~4 个时按列优先填入：
 *       slot 0 左上、slot 1 右上、slot 2 左下、slot 3 右下。</li>
 *   <li>实际尺寸由 {@link android.widget.ImageView} 通过 {@link #setBounds} 设置；
 *       {@link #getIntrinsicWidth()} / {@link #getIntrinsicHeight()} 给一个参考值，
 *       但 {@link android.widget.ImageView#setImageDrawable} 在 fitCenter 等模式下会拉伸。</li>
 * </ul>
 */
public class FolderIconDrawable extends Drawable {

  /** 参考固有尺寸，单位 px。最终大小由 setBounds 决定。 */
  private static final int INTRINSIC_SIZE = 144;

  /** 外边距（占总尺寸比例）。 */
  private static final float OUTER_INSET_RATIO = 0.06f;
  /** 迷你图标网格内单元格边长占内区比例。剩下空间作为间隙。 */
  private static final float CELL_RATIO = 0.42f;
  /** 网格内两单元之间的间隙占内区比例。 */
  private static final float GAP_RATIO = 0.08f;
  /** 描边宽度占总尺寸比例。 */
  private static final float STROKE_RATIO = 0.012f;

  private final List<Drawable> miniIcons;
  private final Paint backgroundPaint;
  private final Paint borderPaint;
  private final RectF tmpRect = new RectF();

  /**
   * @param icons 最多 4 个迷你图标；多余的忽略，少于 4 个空位留白。null 视为空列表。
   */
  public FolderIconDrawable(List<Drawable> icons) {
    this.miniIcons = new ArrayList<>(4);
    if (icons != null) {
      for (Drawable d : icons) {
        if (d == null) continue;
        this.miniIcons.add(d);
        if (this.miniIcons.size() >= 4) break;
      }
    }

    backgroundPaint = new Paint();
    backgroundPaint.setStyle(Paint.Style.FILL);
    backgroundPaint.setColor(0xFFFFFFFF);

    borderPaint = new Paint();
    borderPaint.setStyle(Paint.Style.STROKE);
    borderPaint.setColor(0xFF000000);
  }

  @Override
  public void draw(Canvas canvas) {
    Rect bounds = getBounds();
    if (bounds.isEmpty()) return;

    int size = Math.min(bounds.width(), bounds.height());
    float left = bounds.left + (bounds.width() - size) / 2f;
    float top = bounds.top + (bounds.height() - size) / 2f;

    float stroke = Math.max(1f, size * STROKE_RATIO);
    borderPaint.setStrokeWidth(stroke);

    // 外圆角矩形容器
    float corner = size * 0.10f;
    tmpRect.set(left + stroke / 2f, top + stroke / 2f,
        left + size - stroke / 2f, top + size - stroke / 2f);
    canvas.drawRoundRect(tmpRect, corner, corner, backgroundPaint);
    canvas.drawRoundRect(tmpRect, corner, corner, borderPaint);

    // 内区（去除外边距）
    float inset = size * OUTER_INSET_RATIO;
    float innerLeft = left + inset;
    float innerTop = top + inset;
    float innerSize = size - inset * 2f;

    float cellSize = innerSize * CELL_RATIO;
    float gap = innerSize * GAP_RATIO;
    // 2x2 居中：两单元 + 一间隙占 2*CELL + GAP，剩余左右各 (innerSize - (2*CELL+GAP))/2
    float gridSide = 2 * cellSize + gap;
    float gridOffset = (innerSize - gridSide) / 2f;

    for (int i = 0; i < 4; i++) {
      int col = i % 2;
      int row = i / 2;
      float cellLeft = innerLeft + gridOffset + col * (cellSize + gap);
      float cellTop = innerTop + gridOffset + row * (cellSize + gap);

      if (i < miniIcons.size()) {
        Drawable d = miniIcons.get(i);
        // 保存旧 bounds，绘后还原，避免影响共享实例。
        Rect prev = new Rect(d.getBounds());
        d.setBounds(
            Math.round(cellLeft),
            Math.round(cellTop),
            Math.round(cellLeft + cellSize),
            Math.round(cellTop + cellSize));
        d.draw(canvas);
        d.setBounds(prev);
      }
      // 空位不绘制任何东西，保持背景白。
    }
  }

  @Override
  public int getIntrinsicWidth() {
    return INTRINSIC_SIZE;
  }

  @Override
  public int getIntrinsicHeight() {
    return INTRINSIC_SIZE;
  }

  @Override
  public void setAlpha(int alpha) {
    backgroundPaint.setAlpha(alpha);
    borderPaint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter colorFilter) {
    backgroundPaint.setColorFilter(colorFilter);
    borderPaint.setColorFilter(colorFilter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
