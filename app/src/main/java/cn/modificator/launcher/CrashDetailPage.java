package cn.modificator.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CrashDetailPage extends Activity {

  private static final int MAX_LOG_BYTES = 256 * 1024;

  private TextView btnReLaunch;
  private TextView tvContent;

  @Override
  protected void attachBaseContext(Context newBase) {
    String localeTag = new Config(newBase).getLocaleTag();
    super.attachBaseContext(LocaleManager.wrap(newBase, localeTag));
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    initViews();
  }

  private void initViews() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.WHITE);

    ScrollView scrollView = new ScrollView(this);
    tvContent = new TextView(this);
    tvContent.setTextColor(Color.BLACK);
    tvContent.setTextSize(12);

    scrollView.addView(tvContent);

    root.addView(scrollView, new LinearLayout.LayoutParams(-1, -1, 1));

    btnReLaunch = new TextView(this);
    btnReLaunch.setText("Restart Launcher");
    btnReLaunch.setTextColor(Color.BLACK);
    btnReLaunch.setGravity(Gravity.CENTER);

    View divider = new View(this);
    divider.setBackgroundColor(Color.BLACK);
    root.addView(divider, new ViewGroup.LayoutParams(-1, 1));
    root.addView(btnReLaunch, new LinearLayout.LayoutParams(-1, Utils.dp2Px(this, 40)));

    setContentView(root);
  }

  private void fillErrorContent() {
    btnReLaunch.setOnClickListener(v -> {
      Intent intent = new Intent(CrashDetailPage.this, Launcher.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
      startActivity(intent);
      finish();
    });

    tvContent.setText("");
    String title = "Oh! It's Crashed.";
    SpannableString titleSpan = new SpannableString(title);
    titleSpan.setSpan(new AbsoluteSizeSpan(25, true), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    tvContent.append(titleSpan);
    tvContent.append("\nーーーーーーーーーーーーーーーーーーーー\n");
    tvContent.append("Please screenshot and give me feedback.\n");
    tvContent.append("email    : yunshangcn@gmail.com\n");
    tvContent.append("telegram : https://t.me/EInkLauncher\n");
    tvContent.append("github issues : https://github.com/Modificator/E-Ink-Launcher\n");
    tvContent.append("Thanks.");
    tvContent.append("\nーーーーーーーーーーーーーーーーーーーー\n");

    if (getIntent().hasExtra("crashFile")) {
      String fileName = getIntent().getStringExtra("crashFile");
      File crashFile = new File(getExternalFilesDir("crash"), fileName);
      tvContent.append(readLogSafely(crashFile));
    }
  }

  private static CharSequence readLogSafely(File file) {
    if (!file.exists() || !file.isFile()) return "";
    StringBuilder out = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      char[] buffer = new char[4096];
      int read;
      int total = 0;
      while ((read = reader.read(buffer)) != -1 && total < MAX_LOG_BYTES) {
        int allow = Math.min(read, MAX_LOG_BYTES - total);
        out.append(buffer, 0, allow);
        total += allow;
      }
      if (total >= MAX_LOG_BYTES) {
        out.append("\n... (log truncated)\n");
      }
    } catch (Throwable ignored) {
    }
    return out;
  }

  @Override
  protected void onStart() {
    super.onStart();
    fillErrorContent();
  }
}
