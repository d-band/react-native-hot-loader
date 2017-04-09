package cn.reactnative.modules.update;

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
import com.facebook.react.cxxbridge.JSBundleLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class UpdateModule extends ReactContextBaseJavaModule{
    private UpdateContext updateContext;

    public UpdateModule(ReactApplicationContext reactContext, UpdateContext updateContext) {
        super(reactContext);
        this.updateContext = updateContext;
    }

    public UpdateModule(ReactApplicationContext reactContext) {
        this(reactContext, new UpdateContext(reactContext.getApplicationContext()));
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("downloadRootDir", updateContext.getRootDir());
        constants.put("packageVersion", updateContext.getPackageVersion());
        constants.put("currentVersion", updateContext.getCurrentVersion());
        boolean isFirstTime = updateContext.isFirstTime();
        constants.put("isFirstTime", isFirstTime);
        if (isFirstTime) {
            updateContext.clearFirstTime();
        }
        boolean isRolledBack = updateContext.isRolledBack();
        constants.put("isRolledBack", isRolledBack);
        if (isRolledBack) {
            updateContext.clearRollbackMark();
        }
        return constants;
    }

    @Override
    public String getName() {
        return "RCTHotUpdate";
    }

    @ReactMethod
    public void downloadUpdate(ReadableMap options, final Promise promise){
        String url = options.getString("updateUrl");
        String hash = options.getString("hashName");
        updateContext.downloadFile(url, hash, new UpdateContext.DownloadFileListener() {
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
                updateContext.switchVersion(hash);
                try {
                    Activity activity = getCurrentActivity();
                    Application application = activity.getApplication();
                    ReactInstanceManager instanceManager = ((ReactApplication) application).getReactNativeHost().getReactInstanceManager();

                    Field bundleLoaderField = instanceManager.getClass().getDeclaredField("mBundleLoader");
                    Class<?> jsBundleLoaderClass = Class.forName("com.facebook.react.cxxbridge.JSBundleLoader");
                    Method createFileLoaderMethod = jsBundleLoaderClass.getMethod("createFileLoader", String.class);

                    String jsBundleFile = UpdateContext.getBundleUrl(application);
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
                updateContext.switchVersion(hash);
            }
        });
    }

    @ReactMethod
    public void markSuccess() {
        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateContext.markSuccess();
            }
        });
    }
}
