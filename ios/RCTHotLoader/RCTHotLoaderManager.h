#import <Foundation/Foundation.h>

@interface RCTHotLoaderManager : NSObject


- (BOOL)createDir:(NSString *)dir;

- (void)unzipFileAtPath:(NSString *)path
          toDestination:(NSString *)destination
        progressHandler:(void (^)(NSString *entry, long entryNumber, long total))progressHandler
      completionHandler:(void (^)(NSString *path, BOOL succeeded, NSError *error))completionHandler;

- (void)copyFiles:(NSDictionary *)filesDic
          fromDir:(NSString *)fromDir
            toDir:(NSString *)toDir
          deletes:(NSDictionary *)deletes
       completionHandler:(void (^)(NSError *error))completionHandler;

- (void)removeFile:(NSString *)filePath
 completionHandler:(void (^)(NSError *error))completionHandler;

@end
