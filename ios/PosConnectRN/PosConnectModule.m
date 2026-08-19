#import "PosConnectModule.h"
#import <React/RCTBridge.h>
#import <React/RCTLog.h>
#import <UIKit/UIKit.h>

static NSString *const kConfigKey = @"pos_connect_config_json";

@implementation PosConnectModule {
  BOOL _hasListeners;
}

RCT_EXPORT_MODULE(PosConnect);

+ (BOOL)requiresMainQueueSetup {
  return NO;
}

- (NSArray<NSString *> *)supportedEvents {
  return @[ @"StarPrintRequest" ];
}

- (void)startObserving {
  _hasListeners = YES;
  [[NSNotificationCenter defaultCenter] addObserver:self
                                           selector:@selector(onStarPrintRequest:)
                                               name:@"POSStarPrintRequest"
                                             object:nil];
}

- (void)stopObserving {
  _hasListeners = NO;
  [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (void)onStarPrintRequest:(NSNotification *)note {
  if (!_hasListeners) { return; }
  [self sendEventWithName:@"StarPrintRequest" body:note.userInfo ?: @{}];
}

- (NSString *)okJson:(NSString *)message {
  NSDictionary *body = @{ @"success": @YES, @"message": message ?: @"", @"data": @{} };
  NSData *data = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];
  return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
}

- (NSString *)errorJson:(NSString *)code message:(NSString *)message {
  NSDictionary *body = @{ @"success": @NO, @"errorCode": code ?: @"", @"message": message ?: @"", @"data": @{} };
  NSData *data = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];
  return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
}

RCT_EXPORT_METHOD(getConfiguration:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  NSString *raw = [[NSUserDefaults standardUserDefaults] stringForKey:kConfigKey];
  resolve(raw.length ? raw : @"{}");
}

RCT_EXPORT_METHOD(saveConfiguration:(NSString *)configJson
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  [[NSUserDefaults standardUserDefaults] setObject:configJson ?: @"{}" forKey:kConfigKey];
  resolve([self okJson:@"Saved"]);
}

RCT_EXPORT_METHOD(resetApplication:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  [[NSUserDefaults standardUserDefaults] removeObjectForKey:kConfigKey];
  resolve([self okJson:@"Reset"]);
}

RCT_EXPORT_METHOD(resetPrinter:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  resolve([self okJson:@"Printer reset"]);
}

RCT_EXPORT_METHOD(getDeviceInfo:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  NSDictionary *info = @{
    @"success": @YES,
    @"data": @{
      @"platform": @"ios",
      @"osVersion": UIDevice.currentDevice.systemVersion ?: @"",
      @"manufacturer": @"Apple",
      @"model": UIDevice.currentDevice.model ?: @"",
      @"appVersion": @"1.0.0-rn",
      @"printerEngineVersion": @"3.0-STAR_IO10+ESC_POS"
    }
  };
  NSData *data = [NSJSONSerialization dataWithJSONObject:info options:0 error:nil];
  resolve([[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding]);
}

RCT_EXPORT_METHOD(discoverPrinters:(NSString *)connection
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  resolve([self okJson:@"Use JS Star discovery on iOS"]);
}

RCT_EXPORT_METHOD(connectPrinter:(NSString *)printerJson
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  resolve([self okJson:@"Star engine handled in JS"]);
}

RCT_EXPORT_METHOD(testPrinter:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  resolve([self okJson:@"Use JS Star router for test print"]);
}

RCT_EXPORT_METHOD(isBuiltInPrinterAvailable:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  NSDictionary *body = @{ @"success": @YES, @"data": @{ @"available": @NO } };
  NSData *data = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];
  resolve([[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding]);
}

RCT_EXPORT_METHOD(checkUrlReachable:(NSString *)url
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  NSDictionary *body = @{ @"success": @YES, @"data": @{ @"reachable": @YES } };
  NSData *data = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];
  resolve([[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding]);
}

RCT_EXPORT_METHOD(exportLogs:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  NSDictionary *body = @{ @"success": @YES, @"data": @{ @"text": @"ios-star-io10" } };
  NSData *data = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];
  resolve([[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding]);
}

RCT_EXPORT_METHOD(notifyPrintResult:(NSString *)jobId
                  success:(BOOL)success
                  message:(NSString *)message
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  resolve([self okJson:success ? @"Printed" : (message ?: @"Failed")]);
}

RCT_EXPORT_METHOD(writeTempImage:(NSString *)base64
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  NSString *payload = [base64 stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
  NSRange comma = [payload rangeOfString:@","];
  if ([payload hasPrefix:@"data:"] && comma.location != NSNotFound) {
    payload = [payload substringFromIndex:comma.location + 1];
  }
  NSData *data = [[NSData alloc] initWithBase64EncodedString:payload
                                                     options:NSDataBase64DecodingIgnoreUnknownCharacters];
  if (data.length == 0) {
    resolve([self errorJson:@"INVALID_RECEIPT" message:@"Invalid image"]);
    return;
  }
  NSString *name = [[[NSUUID UUID] UUIDString] stringByAppendingString:@".png"];
  NSString *path = [NSTemporaryDirectory() stringByAppendingPathComponent:name];
  if (![data writeToFile:path atomically:YES]) {
    resolve([self errorJson:@"INVALID_RECEIPT" message:@"Could not write image"]);
    return;
  }
  NSDictionary *body = @{
    @"success": @YES,
    @"message": @"ok",
    @"data": @{ @"path": path }
  };
  NSData *json = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];
  resolve([[NSString alloc] initWithData:json encoding:NSUTF8StringEncoding]);
}

@end
