#import <React/RCTViewManager.h>
#import <React/RCTView.h>
#import <WebKit/WebKit.h>

@interface POSWebView : WKWebView <WKScriptMessageHandler, WKNavigationDelegate>
- (void)loadPosUrl:(NSString *)url;
@property (nonatomic, copy) RCTDirectEventBlock onLoadProgress;
@property (nonatomic, copy) RCTDirectEventBlock onLoadEnd;
@property (nonatomic, copy) RCTDirectEventBlock onError;
@end

@interface POSWebViewManager : RCTViewManager
@end
