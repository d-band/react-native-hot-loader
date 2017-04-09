//
//  RCTHotUpdate.h
//  RCTHotUpdate
//
//  Created by LvBingru on 2/19/16.
//  Copyright © 2016 erica. All rights reserved.
//

#import <React/RCTEventEmitter.h>
#import <React/RCTBridgeModule.h>

@interface RCTHotUpdate : RCTEventEmitter<RCTBridgeModule>

+ (NSURL *)bundleURL;

@end
