#import "RCTHotLoader.h"
#import "RCTHotLoaderDownloader.h"
#import "RCTHotLoaderManager.h"

#import <React/RCTConvert.h>
#import <React/RCTLog.h>

//
static NSString *const keyUpdateInfo = @"RN_HOTLOADER_INFO_KEY";
static NSString *const paramPackageVersion = @"packageVersion";
static NSString *const paramLastVersion = @"lastVersion";
static NSString *const paramCurrentVersion = @"currentVersion";
static NSString *const paramIsFirstTime = @"isFirstTime";
static NSString *const paramIsFirstLoadOk = @"isFirstLoadOK";
static NSString *const keyFirstLoadMarked = @"RN_HOTLOADER_FIRSTLOADMARKED_KEY";
static NSString *const keyRolledBackMarked = @"RN_HOTLOADER_ROLLEDBACKMARKED_KEY";
static NSString *const KeyPackageUpdatedMarked = @"RN_HOTLOADER_ISPACKAGEUPDATEDMARKED_KEY";

// app info
static NSString * const AppVersionKey = @"appVersion";
static NSString * const BuildVersionKey = @"buildVersion";

// file def
static NSString * const BUNDLE_FILE_NAME = @"main.jsbundle";

// error def
static NSString * const ERROR_OPTIONS = @"options error";
static NSString * const ERROR_FILE_OPERATION = @"file operation error";

// event def
static NSString * const EVENT_PROGRESS_DOWNLOAD = @"RCTHotLoaderDownloadProgress";
static NSString * const EVENT_PROGRESS_UNZIP = @"RCTHotLoaderUnzipProgress";
static NSString * const PARAM_PROGRESS_HASHNAME = @"hashname";
static NSString * const PARAM_PROGRESS_RECEIVED = @"received";
static NSString * const PARAM_PROGRESS_TOTAL = @"total";


typedef NS_ENUM(NSInteger, HotLoaderType) {
    HotLoaderTypeFullDownload = 1,
};

@implementation RCTHotLoader {
    RCTHotLoaderManager *_fileManager;
}

@synthesize methodQueue = _methodQueue;

RCT_EXPORT_MODULE(RCTHotLoader);

- (NSArray<NSString *> *)supportedEvents
{
    return @[EVENT_PROGRESS_DOWNLOAD,EVENT_PROGRESS_UNZIP];
}

+ (NSURL *)bundleURL
{
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    
    NSDictionary *updateInfo = [defaults dictionaryForKey:keyUpdateInfo];
    if (updateInfo) {
        NSString *curPackageVersion = [RCTHotLoader packageVersion];
        NSString *packageVersion = [updateInfo objectForKey:paramPackageVersion];
        
        BOOL needClearUpdateInfo = ![curPackageVersion isEqualToString:packageVersion];
        if (needClearUpdateInfo) {
            [defaults setObject:nil forKey:keyUpdateInfo];
            [defaults setObject:@(YES) forKey:KeyPackageUpdatedMarked];
            [defaults synchronize];
            // ...need clear files later
        }
        else {
            NSString *curVersion = updateInfo[paramCurrentVersion];
            NSString *lastVersion = updateInfo[paramLastVersion];
            
            BOOL isFirstTime = [updateInfo[paramIsFirstTime] boolValue];
            BOOL isFirstLoadOK = [updateInfo[paramIsFirstLoadOk] boolValue];
            
            NSString *loadVersioin = curVersion;
            BOOL needRollback = (isFirstTime == NO && isFirstLoadOK == NO) || loadVersioin.length<=0;
            if (needRollback) {
                loadVersioin = lastVersion;
                
                if (lastVersion.length) {
                    // roll back to last version
                    [defaults setObject:@{paramCurrentVersion:lastVersion,
                                          paramIsFirstTime:@(NO),
                                          paramIsFirstLoadOk:@(YES),
                                          paramPackageVersion:curPackageVersion}
                                 forKey:keyUpdateInfo];
                }
                else {
                    // roll back to bundle
                    [defaults setObject:nil forKey:keyUpdateInfo];
                }
                [defaults setObject:@(YES) forKey:keyRolledBackMarked];
                [defaults synchronize];
                // ...need clear files later
            }
            else if (isFirstTime){
                NSMutableDictionary *newInfo = [[NSMutableDictionary alloc] initWithDictionary:updateInfo];
                newInfo[paramIsFirstTime] = @(NO);
                [defaults setObject:newInfo forKey:keyUpdateInfo];
                [defaults setObject:@(YES) forKey:keyFirstLoadMarked];
                [defaults synchronize];
            }
            
            if (loadVersioin.length) {
                NSString *downloadDir = [RCTHotLoader downloadDir];
                
                NSString *bundlePath = [[downloadDir stringByAppendingPathComponent:loadVersioin] stringByAppendingPathComponent:BUNDLE_FILE_NAME];
                if ([[NSFileManager defaultManager] fileExistsAtPath:bundlePath isDirectory:NULL]) {
                    NSURL *bundleURL = [NSURL fileURLWithPath:bundlePath];
                    return bundleURL;
                }
            }
        }
    }
    
    return [RCTHotLoader binaryBundleURL];
}

- (NSDictionary *)constantsToExport
{
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    
    NSMutableDictionary *ret = [NSMutableDictionary new];
    ret[@"downloadRootDir"] = [RCTHotLoader downloadDir];
    ret[@"packageVersion"] = [RCTHotLoader packageVersion];
    ret[@"isRolledBack"] = [defaults objectForKey:keyRolledBackMarked];
    ret[@"isFirstTime"] = [defaults objectForKey:keyFirstLoadMarked];
    NSDictionary *updateInfo = [defaults dictionaryForKey:keyUpdateInfo];
    ret[@"currentVersion"] = [updateInfo objectForKey:paramCurrentVersion];
    
    // clear isFirstTimemarked
    if ([[defaults objectForKey:keyFirstLoadMarked] boolValue]) {
        [defaults setObject:nil forKey:keyFirstLoadMarked];
    }
    
    // clear rolledbackmark
    if ([[defaults objectForKey:keyRolledBackMarked] boolValue]) {
        [defaults setObject:nil forKey:keyRolledBackMarked];
        [self clearInvalidFiles];
    }
    
    // clear packageupdatemarked
    if ([[defaults objectForKey:KeyPackageUpdatedMarked] boolValue]) {
        [defaults setObject:nil forKey:KeyPackageUpdatedMarked];
        [self clearInvalidFiles];
    }
    [defaults synchronize];

    return ret;
}

- (instancetype)init
{
    self = [super init];
    if (self) {
        _fileManager = [RCTHotLoaderManager new];
    }
    return self;
}

RCT_EXPORT_METHOD(downloadUpdate:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    [self hotUpdate:HotLoaderTypeFullDownload options:options callback:^(NSError *error) {
        if (error) {
            reject([NSString stringWithFormat: @"%lu", (long)error.code], error.localizedDescription, error);
        }
        else {
            resolve(nil);
        }
    }];
}

RCT_EXPORT_METHOD(setNeedUpdate:(NSDictionary *)options)
{
    NSString *hashName = options[@"hashName"];
    if (hashName.length) {
        NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
        NSString *lastVersion = nil;
        if ([defaults objectForKey:keyUpdateInfo]) {
            NSDictionary *updateInfo = [defaults objectForKey:keyUpdateInfo];
            lastVersion = updateInfo[paramCurrentVersion];
        }
        
        NSMutableDictionary *newInfo = [[NSMutableDictionary alloc] init];
        newInfo[paramCurrentVersion] = hashName;
        newInfo[paramLastVersion] = lastVersion;
        newInfo[paramIsFirstTime] = @(YES);
        newInfo[paramIsFirstLoadOk] = @(NO);
        newInfo[paramPackageVersion] = [RCTHotLoader packageVersion];
        [defaults setObject:newInfo forKey:keyUpdateInfo];
        
        [defaults synchronize];
    }
}

RCT_EXPORT_METHOD(reloadUpdate:(NSDictionary *)options)
{
    NSString *hashName = options[@"hashName"];
    if (hashName.length) {
        [self setNeedUpdate:options];
        
        // reload
        dispatch_async(dispatch_get_main_queue(), ^{
            [super.bridge setValue:[[self class] bundleURL] forKey:@"bundleURL"];
            [super.bridge reload];
        });
    }
}

RCT_EXPORT_METHOD(markSuccess)
{
    // update package info
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    NSMutableDictionary *packageInfo = [[NSMutableDictionary alloc] initWithDictionary:[defaults objectForKey:keyUpdateInfo]];
    [packageInfo setObject:@(NO) forKey:paramIsFirstTime];
    [packageInfo setObject:@(YES) forKey:paramIsFirstLoadOk];
    [defaults setObject:packageInfo forKey:keyUpdateInfo];
    [defaults synchronize];
    
    // clear other package dir
    [self clearInvalidFiles];
}

#pragma mark - private
- (void)hotUpdate:(HotLoaderType)type options:(NSDictionary *)options callback:(void (^)(NSError *error))callback
{
    NSString *updateUrl = [RCTConvert NSString:options[@"updateUrl"]];
    NSString *hashName = [RCTConvert NSString:options[@"hashName"]];
    if (updateUrl.length<=0 || hashName.length<=0) {
        callback([self errorWithMessage:ERROR_OPTIONS]);
        return;
    }
    
    NSString *dir = [RCTHotLoader downloadDir];
    BOOL success = [_fileManager createDir:dir];
    if (!success) {
        callback([self errorWithMessage:ERROR_FILE_OPERATION]);
        return;
    }
    
    NSString *zipFilePath = [dir stringByAppendingPathComponent:[NSString stringWithFormat:@"%@%@",hashName, [self zipExtension:type]]];

    RCTLogInfo(@"HotLoader -- download file %@", updateUrl);
    [RCTHotLoaderDownloader download:updateUrl savePath:zipFilePath progressHandler:^(long long receivedBytes, long long totalBytes) {
        [self sendEventWithName:EVENT_PROGRESS_DOWNLOAD
                                                     body:@{
                                                            PARAM_PROGRESS_HASHNAME:hashName,
                                                            PARAM_PROGRESS_RECEIVED:[NSNumber numberWithLongLong:receivedBytes],
                                                            PARAM_PROGRESS_TOTAL:[NSNumber numberWithLongLong:totalBytes]
                                                            }];
    } completionHandler:^(NSString *path, NSError *error) {
        if (error) {
            callback(error);
        }
        else {
            RCTLogInfo(@"HotLoader -- unzip file %@", zipFilePath);
            NSString *unzipFilePath = [dir stringByAppendingPathComponent:hashName];
            [_fileManager unzipFileAtPath:zipFilePath toDestination:unzipFilePath progressHandler:^(NSString *entry,long entryNumber, long total) {
                [self sendEventWithName:EVENT_PROGRESS_UNZIP
                                                             body:@{
                                                                    PARAM_PROGRESS_HASHNAME:hashName,
                                                                    PARAM_PROGRESS_RECEIVED:[NSNumber numberWithLong:entryNumber],
                                                                    PARAM_PROGRESS_TOTAL:[NSNumber numberWithLong:total]
                                                                    }];
                
            } completionHandler:^(NSString *path, BOOL succeeded, NSError *error) {
                dispatch_async(_methodQueue, ^{
                    if (error) {
                        callback(error);
                    }
                    else {
                        callback(nil);
                    }
                });
            }];
        }
    }];
}

- (void)clearInvalidFiles
{
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    NSDictionary *updateInfo = [defaults objectForKey:keyUpdateInfo];
    NSString *curVersion = [updateInfo objectForKey:paramCurrentVersion];
    
    NSString *downloadDir = [RCTHotLoader downloadDir];
    NSError *error = nil;
    NSArray *list = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:downloadDir error:&error];
    if (error) {
        return;
    }
    
    for(NSString *fileName in list) {
        if (![fileName isEqualToString:curVersion]) {
            [_fileManager removeFile:[downloadDir stringByAppendingPathComponent:fileName] completionHandler:nil];
        }
    }
}

- (NSString *)zipExtension:(HotLoaderType)type
{
    switch (type) {
        case HotLoaderTypeFullDownload:
            return @".ppk";
        default:
            break;
    }
}

- (NSError *)errorWithMessage:(NSString *)errorMessage
{
    return [NSError errorWithDomain:@"rn.hotupdate"
                               code:-1
                           userInfo:@{ NSLocalizedDescriptionKey: errorMessage}];
}

+ (NSString *)downloadDir
{
    NSString *directory = [NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES) firstObject];
    NSString *downloadDir = [directory stringByAppendingPathComponent:@"_update"];
    
    return downloadDir;
}

+ (NSURL *)binaryBundleURL
{
    NSURL *url = [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
    return url;
}

+ (NSString *)packageVersion
{
    static NSString *version = nil;

    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        NSDictionary *infoDictionary = [[NSBundle mainBundle] infoDictionary];
        version = [infoDictionary objectForKey:@"CFBundleShortVersionString"];
    });
    return version;
}

@end
