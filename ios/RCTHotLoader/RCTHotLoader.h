#import <React/RCTEventEmitter.h>
#import <React/RCTBridgeModule.h>

@interface RCTHotLoader : RCTEventEmitter<RCTBridgeModule>

+ (NSURL *)bundleURL;

@end
