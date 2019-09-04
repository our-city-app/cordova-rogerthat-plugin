/*
 * Copyright 2019 Green Valley Belgium NV
 * NOTICE: THIS FILE HAS BEEN MODIFIED BY GREEN VALLEY BELGIUM NV IN ACCORDANCE WITH THE APACHE LICENSE VERSION 2.0
 * Copyright 2018 GIG Technology NV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @@license_version:1.6@@
 */

#import "MCTComponentFramework.h"
#import "MCTCordovaScreenBrandingVC.h"
#import "MCTMessageHelper.h"
#import "MCTRogerthatCordovaPlugin.h"
#import "MCTSystemPlugin.h"
#import "MCTUIUtils.h"


#pragma mark - CDVInvokedUrlCommand+Additions

@interface CDVInvokedUrlCommand (MCTInvokedUrlCommandAdditions)

+ (instancetype)commandWithCallbackId:(NSString *)callbackId;
- (NSDictionary *)params;

@end


@implementation CDVInvokedUrlCommand (MCTInvokedUrlCommandAdditions)

+ (instancetype)commandWithCallbackId:(NSString *)callbackId
{
    return [[CDVInvokedUrlCommand alloc] initWithArguments:nil
                                                callbackId:callbackId
                                                 className:nil
                                                methodName:nil];
}

- (NSDictionary *)params
{
    return self.arguments.count ? self.arguments[0] : @{};
}

@end


#pragma mark - MCTRogerthatCordovaPlugin

@interface MCTRogerthatCordovaPlugin ()

@property (nonatomic, copy) NSString *callbackId;
@property (nonatomic, strong) MCTScreenBrandingHelper *helper;
@end


@implementation MCTRogerthatCordovaPlugin

- (void)pluginInitialize
{
    [super pluginInitialize];

    // [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onPause) name:UIApplicationDidEnterBackgroundNotification object:nil];
    // [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onResume) name:UIApplicationWillEnterForegroundNotification object:nil];

    // Added in 2.5.0
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(pageDidLoad:) name:CDVPageDidLoadNotification object:self.webView];

    //Added in 4.3.0
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(viewWillAppear:) name:CDVViewWillAppearNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(viewDidAppear:) name:CDVViewDidAppearNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(viewWillDisappear:) name:CDVViewWillDisappearNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(viewDidDisappear:) name:CDVViewDidDisappearNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(viewWillLayoutSubviews:) name:CDVViewWillLayoutSubviewsNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(viewDidLayoutSubviews:) name:CDVViewDidLayoutSubviewsNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(viewWillTransitionToSize:) name:CDVViewWillTransitionToSizeNotification object:nil];

    self.helper = [MCTScreenBrandingHelper helperWithViewController:self.vc
                                                            service:self.vc.service
                                                               item:self.vc.item
                                                           delegate:self];
}


#pragma mark - CDV Notifications

- (void)pageDidLoad:(id)sender { HERE(); }
- (void)viewWillAppear:(id)sender { HERE(); }
- (void)viewDidAppear:(id)sender { HERE(); }
- (void)viewWillDisappear:(id)sender { HERE(); }
- (void)viewWillLayoutSubviews:(id)sender { HERE(); }
- (void)viewDidLayoutSubviews:(id)sender { HERE(); }
- (void)viewWillTransitionToSize:(id)sender { HERE(); }

- (void)viewDidDisappear:(id)sender
{
    HERE();
    if (![self.viewController isInNavigationController]) {
        [self.helper stop];
    }
}

- (NSDictionary *)getRequestParams:(CDVInvokedUrlCommand *)command
{
    NSMutableDictionary *d = [[NSMutableDictionary alloc] initWithDictionary:command.params];
    [d setObject:command.callbackId forKey:@"id"];
    return d;
}


#pragma mark - RogerthatPlugin interface

- (void)start:(CDVInvokedUrlCommand *)command
{
    HERE();
    if (self.callbackId) {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                                 messageAsString:@"RogerthatPlugin already running."]
                                    callbackId:command.callbackId];
        return;
    }

    self.callbackId = command.callbackId;

    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
    pluginResult.keepCallback = @(YES);
    [self.commandDelegate sendPluginResult:pluginResult
                                callbackId:command.callbackId];

    [self.helper start];
}

- (void)log:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper log:[self getRequestParams:command]];
    [self commandProcessed:command];
}

- (void)api_call:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper sendApiCall:[self getRequestParams:command]];
    [self commandProcessed:command];
}

- (void)api_resultHandlerConfigured:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper configureApiResultHandler];
    [self commandProcessed:command];
}

- (void)app_exit:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.vc exitApp];
    [self commandProcessed:command];
}

- (void)app_exitWithResult:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.vc exitAppWithResult:[self getRequestParams:command]];
    [self commandProcessed:command];
}

- (void)camera_startScanningQrCode:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper startScanningQrCodeWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                               params:[self getRequestParams:command]];
}

- (void)camera_stopScanningQrCode:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper stopScanningQrCodeWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                              params:[self getRequestParams:command]];
}

- (void)context:(CDVInvokedUrlCommand *)command
{
    HERE();
    NSDictionary *contextDict = [self.vc.context MCT_JSONValue];
    [self commandProcessed:command withResult:@{@"context":OR(contextDict, @{})}];
}

- (void)message_open:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper openMessageWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                       params:[self getRequestParams:command]];
}

- (void)news_count:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper countNewsWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                     params:[self getRequestParams:command]];
}

- (void)news_get:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper getNewsItemWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                       params:[self getRequestParams:command]];
}

- (void)news_list:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper listNewsWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                    params:[self getRequestParams:command]];
}

- (void)security_createKeyPair:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper createKeyPairWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                         params:[self getRequestParams:command]];
}

- (void)security_hasKeyPair:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper hasKeyPairWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                      params:[self getRequestParams:command]];
}

- (void)security_getPublicKey:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper getPublicKeyWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                        params:[self getRequestParams:command]];
}

- (void)security_getSeed:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper getSeedWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                   params:[self getRequestParams:command]];
}

- (void)security_listAddresses:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper listAddressesWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                         params:[self getRequestParams:command]];
}

- (void)security_getAddress:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper getAddressWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                      params:[self getRequestParams:command]];
}


- (void)security_sign:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper signWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                params:[self getRequestParams:command]];
}

- (void)security_verify:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper verifyWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                  params:[self getRequestParams:command]];

}

- (void)security_listKeyPairs:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper listKeyPairsWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                        params:[self getRequestParams:command]];

}

- (void)system_onBackendConnectivityChanged:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper registerBackendConnectivityChangesWithResultHandler:[self defaultResultHandlerWithCommand:command]];
}

- (void)ui_hideKeyboard:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self commandProcessed:command]; // Empty stub
}

- (void)user_put:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper putUserData:[self getRequestParams:command]];
}

- (void)util_embeddedAppTranslations:(CDVInvokedUrlCommand *)command
{
    HERE();
    if (self.vc.embeddedApp == nil) {
        NSString *errorMessage = MCTLocalizedString(@"An unknown error occurred", nil);
        [self commandProcessed:command withError:@{@"code": @"unknown_error_occurred",
                                                   @"message": errorMessage,
                                                   @"exception": errorMessage}];
        return;
    }

    NSString *translations = [[MCTComponentFramework systemPlugin].store translationsForEmbeddedApp:self.vc.embeddedApp];
    NSDictionary *translationsDict = [translations MCT_JSONValue];
    [self commandProcessed:command withResult:@{@"translations":OR(translationsDict, @{})}];
}

- (void)util_isConnectedToInternet:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper isConnectedToInternetWithResultHandler:[self defaultResultHandlerWithCommand:command]];
}

- (void)util_open:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper openNavigationItemWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                              params:[self getRequestParams:command]];
}

- (void)util_playAudio:(CDVInvokedUrlCommand *)command
{
    HERE();
    [self.helper playAudioWithResultHandler:[self defaultResultHandlerWithCommand:command]
                                              params:[self getRequestParams:command]];
}

#pragma mark - RogerthatPlugin helper methods

- (MCTCordovaScreenBrandingVC *)vc
{
    return ((MCTCordovaScreenBrandingVC *)self.viewController);
}

- (MCTScreenBrandingResultHandler)defaultResultHandlerWithCommand:(CDVInvokedUrlCommand *)command
{
    __weak __typeof__(self) weakSelf = self;
    return ^(NSDictionary *result, NSDictionary *error) {
        [weakSelf commandProcessed:command withResult:result withError:error];
    };
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK]
                                callbackId:command.callbackId];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
              withResult:(NSDictionary *)result
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                         messageAsDictionary:result]
                                callbackId:command.callbackId];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
              withError:(NSDictionary *)error
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                         messageAsDictionary:error]
                                callbackId:command.callbackId];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
              withResult:(NSDictionary *)result
               withError:(NSDictionary *)error
{
    if (error) {
        [self commandProcessed:command withError:error];
    } else {
        [self commandProcessed:command withResult:result];
    }
}

- (void)sendCallback:(NSString *)callback withArguments:(id)args
{
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                  messageAsDictionary:@{@"callback": callback,
                                                                        @"args": args}];
    pluginResult.keepCallback = @(YES);
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}

#pragma mark - MCTScreenBrandingDelegate

- (void)setInfo:(NSDictionary *)info
{
    T_UI();
    [self sendCallback:@"setInfo" withArguments:info];
}

- (void)apiResultReceived:(MCTServiceApiCallbackResult *)r
{
    T_UI();
    [self sendCallback:@"apiResult" withArguments:@{@"method": OR(r.method, MCTNull),
                                                    @"result": OR(r.result, MCTNull),
                                                    @"error": OR(r.error, MCTNull),
                                                    @"tag": OR(r.tag, MCTNull)}];
}

- (void)userDataUpdated:(NSDictionary *)userData
{
    T_UI();
    [self sendCallback:@"userDataUpdated" withArguments:userData];
}

- (void)serviceDataUpdated:(NSDictionary *)serviceData
{
    T_UI();
    [self sendCallback:@"serviceDataUpdated" withArguments:serviceData];
}

- (void)qrCodeScanned:(NSDictionary *)result
{
    T_UI();
    [self sendCallback:@"qrCodeScanned" withArguments:result];
}

- (BOOL)canStartScanning
{
    T_UI();
    return YES;
}

- (void)backendConnectivityChanged:(BOOL)connected
{
    T_UI();
    [self sendCallback:@"onBackendConnectivityChanged" withArguments:@(connected)];
}

- (void)onSecurityResultWithRequestId:(NSString *)requestId result:(NSDictionary *)result error:(NSDictionary *)error
{
    [self commandProcessed:[CDVInvokedUrlCommand commandWithCallbackId:requestId]
                withResult:result
                 withError:error];
}

- (void)newsReceived:(NSArray *)ids
{
    [self sendCallback:@"newsReceived" withArguments:ids];
}

- (void)badgeUpdated:(NSDictionary *)params
{
    [self sendCallback:@"badgeUpdated" withArguments:params];
}

- (BOOL)shouldOverrideLoadWithRequest:(NSURLRequest*)request navigationType:(UIWebViewNavigationType)navigationType
{
    if ([request.URL.scheme isEqualToString:@"poke"]) {
        LOG(@"Received %@", request.URL.absoluteString);
        [self.helper pokeWithTag:[request.URL.absoluteString substringFromIndex:[@"poke://" length]]];
        return NO;
    }

    return YES;
}

@end
