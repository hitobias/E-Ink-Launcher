package cn.modificator.launcher.widgets;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.Nullable;

import cn.modificator.launcher.model.FontManager;
import cn.modificator.launcher.model.ObservableFloat;

/**
 * 监听 {@link ObservableFloat} 字体大小变化并实时更新自身。
 * 同时监听 {@link FontManager} 字体变化。
 */
public class ObserverFontTextView extends TextView
    implements ObservableFloat.Listener, FontManager.TypefaceListener {

  public ObserverFontTextView(Context context) {
    super(context);
  }

  public ObserverFontTextView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public ObserverFontTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void onValueChanged(float value) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, value);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    setTypeface(FontManager.get());
    FontManager.addListener(this);
  }

  @Override
  protected void onDetachedFromWindow() {
    FontManager.removeListener(this);
    super.onDetachedFromWindow();
  }

  @Override
  public void onTypefaceChanged(Typeface typeface) {
    setTypeface(typeface);
  }
}
