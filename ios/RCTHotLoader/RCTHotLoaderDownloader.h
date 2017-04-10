#import <Foundation/Foundation.h>

@interface RCTHotLoaderDownloader : NSObject

+ (void)download:(NSString *)downloadPath savePath:(NSString *)savePath
    progressHandler:(void (^)(long long, long long))progressHandler
completionHandler:(void (^)(NSString *path, NSError *error))completionHandler;

@end
