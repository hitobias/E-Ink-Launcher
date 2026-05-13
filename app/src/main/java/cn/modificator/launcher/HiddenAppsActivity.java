package cn.modificator.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.modificator.launcher.model.IconCache;
import cn.modificator.launcher.widgets.AppSearchAdapter;

/**
 * 隐藏应用管理器：列出已被隐藏的 app，可单个或一键恢复。
 */
public class HiddenAppsActivity extends FragmentActivity {

  private Config config;
  private IconCache iconCache;
  private ListView listView;
  private TextView emptyView;
  private AppSearchAdapter adapter;
  private final List<ResolveInfo> resolved = new ArrayList<>();

  @Override
  protected void attachBaseContext(Context newBase) {
    String localeTag = new Config(newBase).getLocaleTag();
    super.attachBaseContext(LocaleManager.wrap(newBase, localeTag));
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    config = new Config(this);
    iconCache = new IconCache();
    setContentView(buildLayout());
    refresh();
  }

  private View buildLayout() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.WHITE);
    int p = Utils.dp2Px(this, 16);
    root.setPadding(p, p, p, p);

    TextView title = new TextView(this);
    title.setText(R.string.setting_hidden_apps_manager);
    title.setTextColor(Color.BLACK);
    title.setTextSize(20);
    title.setPadding(0, 0, 0, Utils.dp2Px(this, 12));
    root.addView(title);

    LinearLayout actionRow = new LinearLayout(this);
    actionRow.setOrientation(LinearLayout.HORIZONTAL);

    Button restoreAll = new Button(this);
    restoreAll.setText(R.string.hidden_restore_all);
    restoreAll.setOnClickListener(v -> restoreAll());
    actionRow.addView(restoreAll, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    Button close = new Button(this);
    close.setText(R.string.dialog_close);
    close.setOnClickListener(v -> finish());
    actionRow.addView(close, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    root.addView(actionRow);

    listView = new ListView(this);
    listView.setDivider(new android.graphics.drawable.ColorDrawable(0xffcccccc));
    listView.setDividerHeight(1);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    lp.topMargin = Utils.dp2Px(this, 8);
    root.addView(listView, lp);

    emptyView = new TextView(this);
    emptyView.setText(R.string.hidden_no_apps);
    emptyView.setTextColor(Color.BLACK);
    emptyView.setTextSize(16);
    emptyView.setGravity(Gravity.CENTER);
    emptyView.setVisibility(View.GONE);
    LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    elp.topMargin = Utils.dp2Px(this, 32);
    root.addView(emptyView, elp);

    return root;
  }

  private void refresh() {
    PackageManager pm = getPackageManager();
    Set<String> hidden = new HashSet<>(config.getHideApps());

    Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    resolved.clear();
    for (ResolveInfo info : pm.queryIntentActivities(main, 0)) {
      if (info.activityInfo != null
          && hidden.contains(info.activityInfo.packageName)) {
        resolved.add(info);
      }
    }

    if (resolved.isEmpty()) {
      listView.setVisibility(View.GONE);
      emptyView.setVisibility(View.VISIBLE);
      return;
    }
    listView.setVisibility(View.VISIBLE);
    emptyView.setVisibility(View.GONE);

    adapter = new AppSearchAdapter(this, resolved, iconCache);
    listView.setAdapter(adapter);
    listView.setOnItemClickListener((parent, view, pos, id) -> {
      ResolveInfo info = adapter.getItem(pos);
      if (info != null) restoreOne(info.activityInfo.packageName);
    });
  }

  private void restoreOne(String pkg) {
    Set<String> set = new HashSet<>(config.getHideApps());
    set.remove(pkg);
    config.setHideApps(set);
    Toast.makeText(this, R.string.hidden_restored, Toast.LENGTH_SHORT).show();
    refresh();
  }

  private void restoreAll() {
    config.setHideApps(new HashSet<>());
    Toast.makeText(this, R.string.hidden_restored_all, Toast.LENGTH_SHORT).show();
    refresh();
  }
}
