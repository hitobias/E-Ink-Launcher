package cn.modificator.launcher.widgets;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.modificator.launcher.R;
import cn.modificator.launcher.model.IconCache;

/**
 * App 搜索对话框的 ListView 适配器：按 label / packageName 子串模糊匹配。
 */
public class AppSearchAdapter extends BaseAdapter {

  private final List<ResolveInfo> source;
  private final List<ResolveInfo> filtered;
  private final LayoutInflater inflater;
  private final PackageManager pm;
  private final IconCache iconCache;

  public AppSearchAdapter(Context context, List<ResolveInfo> all, IconCache iconCache) {
    this.source = new ArrayList<>(all);
    this.filtered = new ArrayList<>(all);
    this.inflater = LayoutInflater.from(context);
    this.pm = context.getPackageManager();
    this.iconCache = iconCache;
  }

  public void filter(String query) {
    filtered.clear();
    if (query == null || query.trim().isEmpty()) {
      filtered.addAll(source);
    } else {
      String q = query.trim().toLowerCase(Locale.ROOT);
      for (ResolveInfo info : source) {
        String label = readLabel(info);
        String pkg = info.activityInfo.packageName;
        if (label.toLowerCase(Locale.ROOT).contains(q)
            || pkg.toLowerCase(Locale.ROOT).contains(q)) {
          filtered.add(info);
        }
      }
    }
    notifyDataSetChanged();
  }

  private String readLabel(ResolveInfo info) {
    String pkg = info.activityInfo.packageName;
    CharSequence label = iconCache != null
        ? iconCache.getLabel(pkg, info, pm)
        : info.loadLabel(pm);
    return label != null ? label.toString() : pkg;
  }

  @Override
  public int getCount() {
    return filtered.size();
  }

  @Override
  public ResolveInfo getItem(int position) {
    return position >= 0 && position < filtered.size() ? filtered.get(position) : null;
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    View view = convertView != null
        ? convertView
        : inflater.inflate(R.layout.dialog_app_search_item, parent, false);
    ResolveInfo info = filtered.get(position);
    String pkg = info.activityInfo.packageName;

    ImageView icon = view.findViewById(R.id.searchIcon);
    TextView label = view.findViewById(R.id.searchLabel);

    if (iconCache != null) {
      icon.setImageDrawable(iconCache.getIcon(pkg, info, pm));
      label.setText(iconCache.getLabel(pkg, info, pm));
    } else {
      icon.setImageDrawable(info.loadIcon(pm));
      label.setText(info.loadLabel(pm));
    }
    return view;
  }
}
