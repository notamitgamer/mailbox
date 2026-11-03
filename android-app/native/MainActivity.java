package dev.amit.mail;

import android.content.Context;
import android.content.Intent;
import android.net.MailTo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.BridgeActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
  public static volatile boolean visible = false;
  private static String pending = "";
  private FrameLayout splash;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setStatusBarColor(0xFF121416);
    getWindow().setNavigationBarColor(0xFF121416);

    // Request highest refresh rate (e.g. 120 Hz)
    Display display = getWindowManager().getDefaultDisplay();
    Display.Mode cur = display.getMode();
    Display.Mode best = cur;
    for (Display.Mode m : display.getSupportedModes()) {
      if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()
          && m.getRefreshRate() > best.getRefreshRate()) {
        best = m;
      }
    }
    WindowManager.LayoutParams lp = getWindow().getAttributes();
    lp.preferredDisplayModeId = best.getModeId();
    lp.preferredRefreshRate = best.getRefreshRate();
    getWindow().setAttributes(lp);

    getBridge().getWebView().addJavascriptInterface(new Native(), "MailNative");

    // Capacitor core has no back handling without @capacitor/app, so back used to close the app from anywhere.
    // Ask the web app to close its top layer (mail view / sidebar); leave the app only when nothing is open.
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        getBridge().getWebView().evaluateJavascript(
            "(window.__layerBack&&window.__layerBack())===true",
            v -> {
              if (!"true".equals(v)) finish();
            });
      }
    });
    handleIntent(getIntent());
    showSplash();
  }

  @Override
  public void onResume() {
    super.onResume();
    visible = true;
    getBridge().getWebView().post(() ->
        getBridge().getWebView().evaluateJavascript("window.__nativeResume&&window.__nativeResume()", null));
  }

  @Override
  public void onPause() {
    visible = false;
    super.onPause();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleIntent(intent);
    getBridge().getWebView().post(() ->
        getBridge().getWebView().evaluateJavascript("window.__nativePending&&window.__nativePending()", null));
  }

  /** Turns notification taps, mailto links, or share intents into JSON for the web client. */
  private void handleIntent(Intent i) {
    if (i == null) return;
    try {
      JSONObject o = null;
      String a = i.getAction();
      if (i.hasExtra("mailId")) {
        o = new JSONObject().put("kind", "mail").put("id", i.getStringExtra("mailId"))
            .put("t", i.getStringExtra("mailToken")).put("reply", i.getBooleanExtra("reply", false));
      } else if ((Intent.ACTION_SENDTO.equals(a) || Intent.ACTION_VIEW.equals(a)) && i.getData() != null
          && "mailto".equals(i.getData().getScheme())) {
        MailTo mt = MailTo.parse(i.getData().toString());
        JSONArray to = new JSONArray();
        if (mt.getTo() != null) {
          for (String s : mt.getTo().split("[,;]")) {
            if (!s.trim().isEmpty()) to.put(s.trim());
          }
        }
        o = new JSONObject().put("kind", "compose").put("to", to)
            .put("subject", mt.getSubject() == null ? "" : mt.getSubject())
            .put("body", mt.getBody() == null ? "" : mt.getBody());
      } else if (Intent.ACTION_SEND.equals(a)) {
        String text = i.getStringExtra(Intent.EXTRA_TEXT);
        String subj = i.getStringExtra(Intent.EXTRA_SUBJECT);
        o = new JSONObject().put("kind", "compose").put("to", new JSONArray())
            .put("subject", subj == null ? "" : subj)
            .put("body", text == null ? "" : text);
      }
      if (o != null) {
        pending = o.toString();
        i.replaceExtras(new Bundle());
        i.setData(null);
        i.setAction(Intent.ACTION_MAIN);
      }
    } catch (Exception ignored) {
    }
  }

  /**
   * Material 3 splash: the app icon on the surface colour, nothing moves. It sits where the system splash
   * (see windowSplashScreenAnimatedIcon in the theme) draws the same icon, so the hand-over is seamless.
   */
  private void showSplash() {
    final float dp = getResources().getDisplayMetrics().density;
    splash = new FrameLayout(this);
    splash.setBackgroundColor(0xFF121416);

    ImageView icon = new ImageView(this);
    icon.setImageResource(R.drawable.mail_splash);
    int size = (int) (288 * dp);
    splash.addView(icon, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));

    // Footer pinned to the bottom: brand name plus the same credit as the sign-in page
    final LinearLayout footer = new LinearLayout(this);
    footer.setOrientation(LinearLayout.VERTICAL);
    footer.setGravity(Gravity.CENTER_HORIZONTAL);

    final TextView tvBrand = new TextView(this);
    tvBrand.setText("Mailbox");
    tvBrand.setTextSize(18f);
    tvBrand.setTextColor(0xFFE6E8EA);
    tvBrand.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    tvBrand.setGravity(Gravity.CENTER);
    footer.addView(tvBrand, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    final TextView tvCredit = new TextView(this);
    tvCredit.setText("Mailbox UI built on SmartMail by ThemePixels (MIT licence).\n"
        + "Edited and maintained by notamitgamer.\n\n"
        + "\u00A9 2026\u2013present notamitgamer \u00B7 mail.amit.is-a.dev");
    tvCredit.setTextSize(12f);
    tvCredit.setTextColor(0xFF8D949A);
    tvCredit.setGravity(Gravity.CENTER);
    tvCredit.setLineSpacing(0f, 1.3f);
    LinearLayout.LayoutParams creditParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    creditParams.topMargin = (int) (10 * dp);
    footer.addView(tvCredit, creditParams);

    FrameLayout.LayoutParams footerParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    footerParams.bottomMargin = (int) (32 * dp);
    footerParams.leftMargin = (int) (24 * dp);
    footerParams.rightMargin = (int) (24 * dp);
    splash.addView(footer, footerParams);

    addContentView(splash, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    splash.postDelayed(this::hideSplash, 6000); // Fail-safe fallback timeout
  }

  /** Removes the splash at once (no fade or zoom). */
  private void hideSplash() {
    final View v = splash;
    if (v == null) return;
    splash = null;
    if (v.getParent() instanceof ViewGroup) {
      ((ViewGroup) v.getParent()).removeView(v);
    }
  }

  // Android 9 and older: saving into Downloads needs the storage permission. The file waits here until the
  // permission question is answered, then the page is told how it went.
  private String legacyName;
  private String legacyMime;
  private byte[] legacyData;
  private final androidx.activity.result.ActivityResultLauncher<String> legacyPerm = registerForActivityResult(
      new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
        boolean ok = false;
        if (granted && legacyData != null) {
          try {
            ok = writeLegacy(legacyName, legacyMime, legacyData);
          } catch (Exception ignored) {
          }
        }
        legacyData = null;
        final String js = "window.__nativeSaved&&window.__nativeSaved(" + ok + ")";
        getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(js, null));
      });

  /** Writes into the public Downloads folder (never over a file that is already there) and lists it in the Downloads app. */
  private boolean writeLegacy(String name, String type, byte[] data) throws java.io.IOException {
    java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
    if (!dir.exists() && !dir.mkdirs()) return false;
    java.io.File f = new java.io.File(dir, name);
    int dot = name.lastIndexOf('.');
    String stem = dot > 0 ? name.substring(0, dot) : name;
    String ext = dot > 0 ? name.substring(dot) : "";
    for (int i = 1; f.exists() && i < 1000; i++) {
      f = new java.io.File(dir, stem + " (" + i + ")" + ext);
    }
    if (f.exists()) return false;
    try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) {
      os.write(data);
    }
    try { // tapping the entry in the Downloads app opens the file
      ((android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE))
          .addCompletedDownload(f.getName(), f.getName(), true, type, f.getAbsolutePath(), f.length(), true);
    } catch (Exception ignored) { // the file is saved either way
    }
    return true;
  }

  /** Called from the web app as window.MailNative.* */
  private class Native {
    @JavascriptInterface
    public String takePending() {
      String p = pending;
      pending = "";
      return p;
    }

    @JavascriptInterface
    public void hideSplash() {
      runOnUiThread(MainActivity.this::hideSplash);
    }

    @JavascriptInterface
    public void haptic(String kind) {
      final int c = "heavy".equals(kind) ? HapticFeedbackConstants.LONG_PRESS
          : "success".equals(kind) ? (Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.CONTEXT_CLICK)
          : HapticFeedbackConstants.CLOCK_TICK;
      runOnUiThread(() -> getBridge().getWebView().performHapticFeedback(c, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING));
    }

    /** Build number of the installed app (the release workflow sets it to the run number). */
    @JavascriptInterface
    public int versionCode() {
      try {
        android.content.pm.PackageInfo pi = MainActivity.this.getPackageManager().getPackageInfo(MainActivity.this.getPackageName(), 0);
        return (int) (Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
      } catch (Exception e) {
        return 0;
      }
    }

    /** Opens a release download in the browser; only this repo's GitHub URLs are allowed. */
    @JavascriptInterface
    public void openUrl(String u) {
      if (u == null || !u.startsWith("https://github.com/notamitgamer/mailbox/")) return;
      runOnUiThread(() -> MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))));
    }

    /**
     * Saves an attachment (base64) into the Downloads folder. A WebView cannot download a blob itself.
     * Returns "ok" when saved, "" when it failed, and "wait" on Android 9 and older when the storage permission
     * is being asked for: the answer then arrives through window.__nativeSaved(true|false).
     * Android 10+ needs no permission and also offers to open the file; older versions list it in the
     * Downloads app instead.
     */
    @JavascriptInterface
    public String saveFile(String name, String mime, String base64) {
      if (base64 == null) return "";
      try {
        String safe = (name == null ? "" : name).replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").replaceAll("^\\.+", "").trim();
        if (safe.isEmpty()) safe = "attachment";
        final String type = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
        byte[] data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        if (Build.VERSION.SDK_INT < 29) return saveLegacy(safe, type, data);
        android.content.ContentResolver cr = MainActivity.this.getContentResolver();
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safe);
        v.put(android.provider.MediaStore.Downloads.MIME_TYPE, type);
        v.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
        final Uri uri = cr.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return "";
        try (java.io.OutputStream os = cr.openOutputStream(uri)) {
          if (os == null) return "";
          os.write(data);
        }
        v.clear();
        v.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
        cr.update(uri, v, null, null);
        if (!"application/vnd.android.package-archive".equals(type)) { // never launch an installer from a mail
          runOnUiThread(() -> {
            try {
              MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                  .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { // no app can open it: it is still in Downloads
            }
          });
        }
        return "ok";
      } catch (Exception e) {
        return "";
      }
    }

    // ---- Chunked save (Android 10+): the web app sends a big file in pieces, so it is never held as one huge
    // string on either side of the bridge. One file at a time. beginFile returns false on older Android, and
    // the web app then falls back to saveFile. ----
    private Uri outUri;
    private java.io.OutputStream outStream;
    private String outType;

    @JavascriptInterface
    public synchronized boolean beginFile(String name, String mime) {
      abortFile();
      if (Build.VERSION.SDK_INT < 29) return false;
      try {
        String safe = (name == null ? "" : name).replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").replaceAll("^\\.+", "").trim();
        if (safe.isEmpty()) safe = "attachment";
        outType = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safe);
        v.put(android.provider.MediaStore.Downloads.MIME_TYPE, outType);
        v.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
        android.content.ContentResolver cr = MainActivity.this.getContentResolver();
        outUri = cr.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (outUri == null) return false;
        outStream = cr.openOutputStream(outUri);
        if (outStream == null) {
          abortFile();
          return false;
        }
        return true;
      } catch (Exception e) {
        abortFile();
        return false;
      }
    }

    @JavascriptInterface
    public synchronized boolean appendFile(String base64) {
      if (outStream == null || base64 == null) return false;
      try {
        outStream.write(android.util.Base64.decode(base64, android.util.Base64.DEFAULT));
        return true;
      } catch (Exception e) {
        abortFile();
        return false;
      }
    }

    @JavascriptInterface
    public synchronized boolean endFile() {
      if (outStream == null || outUri == null) return false;
      final Uri uri = outUri;
      final String type = outType;
      try {
        outStream.close();
        outStream = null;
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
        MainActivity.this.getContentResolver().update(uri, v, null, null);
        outUri = null;
        if (!"application/vnd.android.package-archive".equals(type)) { // never launch an installer from a mail
          runOnUiThread(() -> {
            try {
              MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                  .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { // no app can open it: it is still in Downloads
            }
          });
        }
        return true;
      } catch (Exception e) {
        abortFile();
        return false;
      }
    }

    /** Drops a half-written file (a failed or cancelled save), so no broken file is left in Downloads. */
    @JavascriptInterface
    public synchronized void abortFile() {
      try {
        if (outStream != null) outStream.close();
      } catch (Exception ignored) {
      }
      try {
        if (outUri != null) MainActivity.this.getContentResolver().delete(outUri, null, null);
      } catch (Exception ignored) {
      }
      outStream = null;
      outUri = null;
    }

    /** Android 9 and older: needs the storage permission, which is asked for the first time a file is saved. */
    private String saveLegacy(String safe, String type, byte[] data) throws java.io.IOException {
      if (androidx.core.content.ContextCompat.checkSelfPermission(MainActivity.this,
          android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return writeLegacy(safe, type, data) ? "ok" : "";
      }
      legacyName = safe;
      legacyMime = type;
      legacyData = data; // kept until the permission answer arrives
      runOnUiThread(() -> legacyPerm.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE));
      return "wait";
    }

    /** Clear notification badges when inbox has no unread messages. */
    @JavascriptInterface
    public void setUnread(int n) {
      if (n <= 0) NotificationManagerCompat.from(MainActivity.this).cancelAll();
    }
  }
}
