import { NativeAppEventEmitter, NativeModules } from 'react-native';

const { HotLoader } = NativeModules;

export const downloadRootDir = HotLoader.downloadRootDir;
export const packageVersion = HotLoader.packageVersion;
export const currentVersion = HotLoader.currentVersion;
export const isFirstTime = HotLoader.isFirstTime;
export const isRolledBack = HotLoader.isRolledBack;

/**
 * Return json:
 * 1. Package was expired:
 *   {
 *     expired: true,
 *     downloadUrl: 'http://appstore/downloadUrl'
 *   }
 * 2. Package is up to date:
 *   {
 *     upToDate: true
 *   }
 * 3. There is available update:
 *   {
 *     update: true,
 *     name: '1.0.3-rc',
 *     hash: 'hash',
 *     description: '添加聊天功能\n修复商城页面BUG',
 *     metaInfo: { silent: true },
 *     updateUrl: 'http://com.example/android.ppk'
 *   }
 */

export async function downloadUpdate(options) {
  if (!options.update) {
    return;
  }

  await HotLoader.downloadUpdate({
    updateUrl: options.updateUrl,
    hashName: options.hash,
  });
  return options.hash;
}

export async function switchVersion(hash) {
  HotLoader.reloadUpdate({
    hashName: hash
  });
}

export async function switchVersionLater(hash) {
  HotLoader.setNeedUpdate({
    hashName: hash
  });
}

export function markSuccess() {
  HotLoader.markSuccess();
}


NativeAppEventEmitter.addListener('RCTHotLoaderDownloadProgress', (params) => {

});

NativeAppEventEmitter.addListener('RCTHotLoaderUnzipProgress', (params) => {

});