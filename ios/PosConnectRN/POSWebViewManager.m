#import "POSWebViewManager.h"
#import <React/RCTUIManager.h>
#import <React/RCTBridge.h>
#import <React/RCTConvert.h>
#import <React/RCTEventDispatcher.h>

// Keys used to store config in NSUserDefaults (must match PosConnectModule.m)
static NSString *const kConfigKey = @"pos_connect_config_json";

@implementation POSWebView {
  WKUserContentController *_controller;
  id<RCTEventDispatcherProtocol> _eventDispatcher;
}

- (instancetype)initWithFrame:(CGRect)frame {
  WKWebViewConfiguration *config = [WKWebViewConfiguration new];
  _controller = [WKUserContentController new];

  // Full bridge JS — all POSNative methods + print hook
  NSString *bridge =
    @"(function(){"
    "function send(method,data){try{window.webkit.messageHandlers.POSNativeBridge.postMessage({method:method,data:data});}catch(e){}"
    "return JSON.stringify({success:true,data:{status:'QUEUED'}});}"
    "var names=['getDeviceInfo','getConfiguration','getPrinterStatus','getPrinterCapabilities','getPrinterSettings','getPrinterWidth','getConnectionStatus','getPrinters','connectPrinter','disconnectPrinter','printReceipt','printText','printImage','printQRCode','printBarcode','testPrinter','openCashDrawer','cutPaper','showPrintDialog'];"
    "window.POSNativeBridge=window.POSNativeBridge||{};"
    "names.forEach(function(n){window.POSNativeBridge[n]=function(d){return send(n,d);};});"
    "if(window.__POS_NATIVE_READY){return;}"
    "var raw=window.POSNativeBridge;"
    "function parse(v){try{return JSON.parse(v);}catch(e){return {success:false,errorCode:'INVALID_RECEIPT',message:String(e)};}}"
    "function asString(d){if(d==null)return '';return typeof d==='string'?d:JSON.stringify(d);}"
    "window.POSNative={"
    "getDeviceInfo:async function(){return parse(raw.getDeviceInfo());},"
    "getConfiguration:async function(){return parse(raw.getConfiguration());},"
    "getPrinterStatus:async function(){return parse(raw.getPrinterStatus());},"
    "getPrinterSettings:async function(){return parse(raw.getPrinterSettings());},"
    "getPrinterCapabilities:async function(){return parse(raw.getPrinterCapabilities());},"
    "getPrinters:async function(){return parse(raw.getPrinters());},"
    "connectPrinter:async function(d){return parse(raw.connectPrinter(asString(d)));},"
    "disconnectPrinter:async function(){return parse(raw.disconnectPrinter());},"
    "printReceipt:async function(d){return parse(raw.printReceipt(asString(d)));},"
    "printText:async function(d){return parse(raw.printText(asString(d)));},"
    "printImage:async function(d){return parse(raw.printImage(asString(d)));},"
    "printQRCode:async function(d){return parse(raw.printQRCode(asString(d)));},"
    "printBarcode:async function(d){return parse(raw.printBarcode(asString(d)));},"
    "testPrinter:async function(){return parse(raw.testPrinter());},"
    "openCashDrawer:async function(){return parse(raw.openCashDrawer());},"
    "cutPaper:async function(){return parse(raw.cutPaper());},"
    "showPrintDialog:async function(d){return parse(raw.showPrintDialog(asString(d)));}"
    "};"
    "window.posNativeBridge=window.posNativeBridge||{};"
    "if(typeof window.posNativeBridge._printResult!=='function'){window.posNativeBridge._printResult=function(){};}"
    "window.ElintPOSNative=window.ElintPOSNative||{printWebContent:function(text){try{var r=parse(raw.printText(JSON.stringify({text:String(text||'')})));return JSON.stringify({ok:r.success===true});}catch(e){return JSON.stringify({ok:false});}}};"
    // print hook — respects showPrintDialog setting
    "var __origPrint=window.print?window.print.bind(window):function(){};"
    "window.print=function(){"
    "var text='';"
    "try{text=(document.body&&(document.body.innerText||document.body.textContent))||'';}catch(e){}"
    "raw.showPrintDialog(JSON.stringify({text:text}));"
    "};"
    "window.__POS_NATIVE_READY=true;"
    "})();";
  WKUserScript *script = [[WKUserScript alloc] initWithSource:bridge
                                                injectionTime:WKUserScriptInjectionTimeAtDocumentStart
                                             forMainFrameOnly:YES];
  [_controller addUserScript:script];
  config.userContentController = _controller;
  self = [super initWithFrame:frame configuration:config];
  if (self) {
    [_controller addScriptMessageHandler:self name:@"POSNativeBridge"];
  }
  return self;
}

- (void)userContentController:(WKUserContentController *)userContentController
      didReceiveScriptMessage:(WKScriptMessage *)message {
  id body = message.body;
  if (![body isKindOfClass:[NSDictionary class]]) { return; }

  NSString *method = body[@"method"] ?: @"printText";
  id data = body[@"data"];
  NSString *text = @"";
  if ([data isKindOfClass:[NSString class]]) {
    text = data;
  } else if (data) {
    NSData *json = [NSJSONSerialization dataWithJSONObject:data options:0 error:nil];
    text = json ? [[NSString alloc] initWithData:json encoding:NSUTF8StringEncoding] : @"";
  }

  // showPrintDialog — read setting, auto-print or show UIAlertController
  if ([method isEqualToString:@"showPrintDialog"]) {
    NSString *printText = text;
    // Try to extract text from JSON payload {text:...}
    NSData *jsonData = [text dataUsingEncoding:NSUTF8StringEncoding];
    if (jsonData) {
      id parsed = [NSJSONSerialization JSONObjectWithData:jsonData options:0 error:nil];
      if ([parsed isKindOfClass:[NSDictionary class]] && parsed[@"text"]) {
        printText = parsed[@"text"];
      }
    }
    if (printText.length == 0) { return; }

    // Read showPrintDialog from saved config
    BOOL showDialog = YES;
    NSString *configRaw = [[NSUserDefaults standardUserDefaults] stringForKey:kConfigKey];
    if (configRaw.length) {
      NSData *cd = [configRaw dataUsingEncoding:NSUTF8StringEncoding];
      id cfg = [NSJSONSerialization JSONObjectWithData:cd options:0 error:nil];
      if ([cfg isKindOfClass:[NSDictionary class]]) {
        id printer = cfg[@"printer"];
        if ([printer isKindOfClass:[NSDictionary class]]) {
          id spd = printer[@"showPrintDialog"];
          if ([spd isKindOfClass:[NSNumber class]]) {
            showDialog = [spd boolValue];
          }
        }
      }
    }

    NSString *finalText = printText;
    if (!showDialog) {
      // Auto-print via Star notification
      [[NSNotificationCenter defaultCenter] postNotificationName:@"POSStarPrintRequest"
                                                          object:nil
                                                        userInfo:@{
                                                          @"jobId": [[NSUUID UUID] UUIDString],
                                                          @"action": @"printText",
                                                          @"text": finalText,
                                                          @"qr": @"",
                                                          @"barcode": @"",
                                                          @"image": @""
                                                        }];
      return;
    }

    // Show UIAlertController on main thread
    dispatch_async(dispatch_get_main_queue(), ^{
      UIViewController *root = [UIApplication sharedApplication].keyWindow.rootViewController;
      while (root.presentedViewController) { root = root.presentedViewController; }
      UIAlertController *alert = [UIAlertController
        alertControllerWithTitle:@"Print Receipt"
        message:@"Send to configured printer?"
        preferredStyle:UIAlertControllerStyleAlert];
      [alert addAction:[UIAlertAction actionWithTitle:@"Print" style:UIAlertActionStyleDefault handler:^(UIAlertAction *a) {
        [[NSNotificationCenter defaultCenter] postNotificationName:@"POSStarPrintRequest"
                                                            object:nil
                                                          userInfo:@{
                                                            @"jobId": [[NSUUID UUID] UUIDString],
                                                            @"action": @"printText",
                                                            @"text": finalText,
                                                            @"qr": @"",
                                                            @"barcode": @"",
                                                            @"image": @""
                                                          }];
      }]];
      [alert addAction:[UIAlertAction actionWithTitle:@"Cancel" style:UIAlertActionStyleCancel handler:nil]];
      [root presentViewController:alert animated:YES completion:nil];
    });
    return;
  }

  // All other print actions → route via Star notification
  NSString *action = method;
  if ([action isEqualToString:@"openCashDrawer"]) { action = @"openDrawer"; }
  if ([action isEqualToString:@"testPrinter"])    { action = @"testPrint"; }
  if ([action isEqualToString:@"printQRCode"])    { action = @"printQR"; }

  NSString *qr      = [action isEqualToString:@"printQR"]      ? text : @"";
  NSString *barcode = [action isEqualToString:@"printBarcode"]  ? text : @"";
  NSString *image   = [action isEqualToString:@"printImage"]    ? text : @"";

  [[NSNotificationCenter defaultCenter] postNotificationName:@"POSStarPrintRequest"
                                                      object:nil
                                                    userInfo:@{
                                                      @"jobId": [[NSUUID UUID] UUIDString],
                                                      @"action": action,
                                                      @"text": text ?: @"",
                                                      @"qr": qr,
                                                      @"barcode": barcode,
                                                      @"image": image
                                                    }];
}

- (void)loadPosUrl:(NSString *)url {
  if (url.length == 0) { return; }
  NSURL *parsed = [NSURL URLWithString:url];
  if (!parsed) { return; }
  self.navigationDelegate = self;
  [self loadRequest:[NSURLRequest requestWithURL:parsed]];
}

// WKNavigationDelegate — progress + error events
- (void)webView:(WKWebView *)webView didStartProvisionalNavigation:(WKNavigation *)navigation {
  [self emitProgress:5];
}

- (void)webView:(WKWebView *)webView didFinishNavigation:(WKNavigation *)navigation {
  [self emitProgress:100];
}

- (void)webView:(WKWebView *)webView didFailNavigation:(WKNavigation *)navigation withError:(NSError *)error {
  [self emitError:error.localizedDescription ?: @"Load failed"];
}

- (void)webView:(WKWebView *)webView didFailProvisionalNavigation:(WKNavigation *)navigation withError:(NSError *)error {
  [self emitError:error.localizedDescription ?: @"Load failed"];
}

- (void)webView:(WKWebView *)webView
  decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction
decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
  decisionHandler(WKNavigationActionPolicyAllow);
}

- (void)emitProgress:(NSInteger)pct {
  if (self.onLoadProgress) {
    self.onLoadProgress(@{ @"progress": @(pct) });
  }
}

- (void)emitError:(NSString *)message {
  if (self.onError) {
    self.onError(@{ @"message": message });
  }
}

@end

@implementation POSWebViewManager

RCT_EXPORT_MODULE(POSWebView)

- (UIView *)view {
  return [POSWebView new];
}

RCT_EXPORT_VIEW_PROPERTY(onLoadProgress, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onLoadEnd, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onError, RCTDirectEventBlock)

RCT_CUSTOM_VIEW_PROPERTY(url, NSString, POSWebView)
{
  [view loadPosUrl:json ? [RCTConvert NSString:json] : @""];
}

@end
