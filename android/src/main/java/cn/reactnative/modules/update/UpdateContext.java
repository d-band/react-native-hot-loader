package cn.reactnative.modules.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;

public class UpdateContext {
    private Context context;
    private File rootDir;

    public static boolean DEBUG = false;

    public UpdateContext(Context context) {
        this.context = context;

        this.rootDir = new File(context.getFilesDir(), "_update");

        if (!rootDir.exists()) {
            rootDir.mkdir();
        }

        this.sp = context.getSharedPreferences("update", Context.MODE_PRIVATE);

        String packageVersion = getPackageVersion();
        if (!packageVersion.equals(this.sp.getString("packageVersion", null))) {
            SharedPreferences.Editor editor = sp.edit();
            editor.clear();
            editor.putString("packageVersion", packageVersion);
            editor.apply();

            this.clearUp();
        }
    }

    public String getRootDir() {
        return rootDir.toString();
    }

    public String getPackageVersion() {
        PackageManager pm = context.getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi.versionName;
        } catch( PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface DownloadFileListener {
        void onDownloadCompleted();
        void onDownloadFailed(Throwable error);
    }

    public void downloadFile(String url, String hashName, DownloadFileListener listener) {
        DownloadTaskParams params = new DownloadTaskParams();
        params.type = DownloadTaskParams.TASK_TYPE_FULL_DOWNLOAD;
        params.url = url;
        params.hash = hashName;
        params.listener = listener;
        params.zipFilePath = new File(rootDir, hashName + ".ppk");
        params.unzipDirectory = new File(rootDir, hashName);
        new DownloadTask().execute(params);
    }

    private SharedPreferences sp;

    public void switchVersion(String hashName) {
        if (!new File(rootDir, hashName).exists()) {
            throw new Error("Hash name not found, must download first.");
        }
        String lastVersion = getCurrentVersion();
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("currentVersion", hashName);
        if (lastVersion != null) {
            editor.putString("lastVersion", lastVersion);
        }
        editor.putBoolean("firstTime", true);
        editor.putBoolean("firstTimeOk", false);
        editor.putBoolean("rolledBack", false);
        editor.apply();
    }

    public String getCurrentVersion() {
        return sp.getString("currentVersion", null);
    }

    public boolean isFirstTime() {
        return sp.getBoolean("firstTime", false);
    }

    public boolean isRolledBack() {
        return sp.getBoolean("rolledBack", false);
    }

    public void markSuccess() {
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("firstTimeOk", true);
        editor.remove("lastVersion");
        editor.apply();

        this.clearUp();
    }

    public void clearFirstTime() {
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("firstTime", false);
        editor.apply();

        this.clearUp();
    }

    public void clearRollbackMark() {
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("rolledBack", false);
        editor.apply();

        this.clearUp();
    }

    public static String getBundleUrl(Context context) {
        return new UpdateContext(context.getApplicationContext()).getBundleUrl();
    }

    public static String getBundleUrl(Context context, String defaultAssetsUrl) {
        return new UpdateContext(context.getApplicationContext()).getBundleUrl(defaultAssetsUrl);
    }

    public String getBundleUrl() {
        return this.getBundleUrl((String) null);
    }

    public String getBundleUrl(String defaultAssetsUrl) {
        String currentVersion = getCurrentVersion();
        if (currentVersion == null) {
            return defaultAssetsUrl;
        }
        // Test should rollback.
        if (!sp.getBoolean("firstTime", false)) {
            if (!sp.getBoolean("firstTimeOk", true)) {
                // Not firstTime, but not ok, so we roll back.
                currentVersion = this.rollBack();
            }
        }
        if (currentVersion == null) {
            return defaultAssetsUrl;
        }
        return (new File(rootDir, currentVersion + "/index.bundle").toString());
    }

    private String rollBack() {
        String lastVersion = sp.getString("lastVersion", null);
        SharedPreferences.Editor editor = sp.edit();
        if (lastVersion == null) {
            editor.remove("currentVersion");
        } else {
            editor.putString("currentVersion", lastVersion);
        }
        editor.putBoolean("firstTimeOk", true);
        editor.putBoolean("firstTime", false);
        editor.putBoolean("rolledBack", true);
        editor.apply();
        return lastVersion;
    }

    private void clearUp() {
        DownloadTaskParams params = new DownloadTaskParams();
        params.type = DownloadTaskParams.TASK_TYPE_CLEAR_UP;
        params.hash = sp.getString("currentVersion", null);
        params.originHash = sp.getString("lastVersion", null);
        params.unzipDirectory = rootDir;
        params.listener = new DownloadFileListener() {
            @Override
            public void onDownloadCompleted() {
            }

            @Override
            public void onDownloadFailed(Throwable error) {
            }
        };
        new DownloadTask().execute(params);
    }
}