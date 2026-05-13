package cn.modificator.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * "关于" 对话框：显示作者 / 致谢 / 简介，文案通过 strings.xml 国际化。
 */
public class AboutDialog {

  private final Context context;

  private AboutDialog(Context context) {
    this.context = context;
  }

  public static AboutDialog getInstance(Context context) {
    return new AboutDialog(context);
  }

  private View initLayout() {
    LinearLayout root = new LinearLayout(context);
    int padding = Utils.dp2Px(context, 15);
    root.setPadding(padding, padding, padding, padding);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xffffffff);

    addTitle(root, context.getString(R.string.app_name), 30);
    addVersion(root);
    addDivider(root);

    TextView authorInfo = makeTextBlock(context.getString(R.string.about_author_info), 18);
    authorInfo.setLineSpacing(authorInfo.getLineSpacingExtra(), 1.2f);
    LinearLayout.LayoutParams authorInfoLP = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    authorInfoLP.topMargin = Utils.dp2Px(context, 10);
    authorInfoLP.bottomMargin = Utils.dp2Px(context, 10);
    root.addView(authorInfo, authorInfoLP);

    addDivider(root);

    TextView thanks = makeTextBlock(context.getString(R.string.about_thanks), 15);
    thanks.setPadding(0, Utils.dp2Px(context, 10), 0, Utils.dp2Px(context, 10));
    root.addView(thanks);

    addDivider(root);

    root.addView(makeTextBlock(context.getString(R.string.about_description), 14));

    return root;
  }

  /**
   * 版本行：取 versionName（含 flavor 後綴，如 "0.2.2-supernote"）+ versionCode。
   * 用戶回報 bug 時長截圖即可看到精確版本，少一輪追問。
   */
  private void addVersion(LinearLayout root) {
    String label = readVersionLabel();
    if (label == null) return;
    TextView tv = new TextView(context);
    tv.setText(label);
    tv.setTextSize(13);
    tv.setTextColor(0xff000000);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = Utils.dp2Px(context, 2);
    lp.bottomMargin = Utils.dp2Px(context, 6);
    root.addView(tv, lp);
  }

  private String readVersionLabel() {
    try {
      PackageInfo pi = context.getPackageManager()
          .getPackageInfo(context.getPackageName(), 0);
      long code = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
          ? pi.getLongVersionCode()
          : pi.versionCode;
      return "v" + pi.versionName + " (" + code + ")";
    } catch (PackageManager.NameNotFoundException e) {
      return null;
    }
  }

  private void addTitle(LinearLayout root, String text, int sizeSp) {
    TextView tv = new TextView(context);
    tv.setText(text);
    tv.setTextSize(sizeSp);
    tv.setTextColor(0xff000000);
    // 长按标题打开诊断信息对话框
    tv.setOnLongClickListener(v -> {
      showDiagnostics();
      return true;
    });
    root.addView(tv);
  }

  private void showDiagnostics() {
    TextView content = new TextView(context);
    content.setText(Diagnostics.collect(context));
    content.setTextColor(0xff000000);
    content.setTextSize(13);
    content.setTextIsSelectable(true);
    int p = Utils.dp2Px(context, 16);
    content.setPadding(p, p, p, p);
    new AlertDialog.Builder(context)
        .setTitle("Diagnostics")
        .setView(content)
        .setPositiveButton(R.string.dialog_close, null)
        .show();
  }

  private TextView makeTextBlock(String text, int sizeSp) {
    TextView tv = new TextView(context);
    tv.setText(text);
    tv.setTextSize(sizeSp);
    tv.setTextColor(0xff000000);
    return tv;
  }

  private void addDivider(LinearLayout root) {
    View line = new View(context);
    line.setBackgroundColor(0xff000000);
    root.addView(line, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Utils.dp2Px(context, 1)));
  }

  public void show() {
    new AlertDialog.Builder(context)
        .setView(initLayout())
        .setPositiveButton(R.string.dialog_close, null)
        .show();
  }
}
