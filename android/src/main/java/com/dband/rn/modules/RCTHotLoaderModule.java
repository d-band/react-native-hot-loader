package com.dband.rn.modules;

import android.app.Activity;
import android.app.Application;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RCTHotLoaderModule extends ReactContextBaseJavaModule{
    private RCTHotLoaderContext loaderContext;

    public RCTHotLoaderModule(ReactApplicationContext reactContext, RCTHotLoaderContext loaderContext) {
        super(reactContext);
        this.loaderContext = loaderContext;
    }

    public RCTHotLoaderModule(ReactApplicationContext reactContext) {
        this(reactContext, new RCTHotLoaderContext(reactContext.getApplicationContext()));
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("downloadRootDir", loaderContext.getRootDir());
        constants.put("packageVersion", loaderContext.getPackageVersion());
        constants.put("currentVersion", loaderContext.getCurrentVersion());
        boolean isFirstTime = loaderContext.isFirstTime();
        constants.put("isFirstTime", isFirstTime);
        if (isFirstTime) {
            loaderContext.clearFirstTime();
        }
        boolean isRolledBack = loaderContext.isRolledBack();
        constants.put("isRolledBack", isRolledBack);
        if (isRolledBack) {
            loaderContext.clearRollbackMark();
        }
        return constants;
    }

    @Override
    public String getName() {
        return "RCTHotLoader";
    }

    @ReactMethod
    public void downloadUpdate(ReadableMap options, final Promise promise){
        String url = options.getString("updateUrl");
        String hash = options.getString("hashName");
        loaderContext.downloadFile(url, hash, new RCTHotLoaderContext.DownloadFileListener() {
            @Override
            public void onDownloadCompleted() {
                promise.resolve(null);
            }

            @Override
            public void onDownloadFailed(Throwable error) {
                promise.reject(error);
            }
        });
    }

    @ReactMethod
    public void reloadUpdate(ReadableMap options) {
        final String hash = options.getString("hashName");

        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loaderContext.switchVersion(hash);
                try {
                    Activity activity = getCurrentActivity();
                    Application application = activity.getApplication();
                    ReactInstanceManager instanceManager = ((ReactApplication) application).getReactNativeHost().getReactInstanceManager();

                    Field bundleLoaderField = instanceManager.getClass().getDeclaredField("mBundleLoader");
                    Class<?> jsBundleLoaderClass = Class.forName("com.facebook.react.cxxbridge.JSBundleLoader");
                    Method createFileLoaderMethod = jsBundleLoaderClass.getMethod("createFileLoader", String.class);

                    String jsBundleFile = RCTHotLoaderContext.getBundleUrl(application);
                    Object newBundleLoader = createFileLoaderMethod.invoke(jsBundleLoaderClass, jsBundleFile);
                    bundleLoaderField.setAccessible(true);
                    bundleLoaderField.set(instanceManager, newBundleLoader);
                    // reload
                    instanceManager.recreateReactContextInBackground();
                } catch (Throwable err) {
                    Log.e("pushy", "Failed to restart application", err);
                }
            }
        });
    }

    @ReactMethod
    public void setNeedUpdate(ReadableMap options) {
        final String hash = options.getString("hashName");

        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loaderContext.switchVersion(hash);
            }
        });
    }

    @ReactMethod
    public void markSuccess() {
        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loaderContext.markSuccess();
            }
        });
    }
}
