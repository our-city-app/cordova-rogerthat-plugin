/**
 * See http://www.rogerthat.net/developers/javascript-api for more info
 */
import { RogerthatError } from './rogerthat-errors';
import { PaymentRequestData, PayWidgetContextData, RogerthatPayments } from './rogerthat-payment';
import {
  GetNewsGroupRequestTO,
  GetNewsGroupResponseTO,
  GetNewsGroupsRequestTO,
  GetNewsGroupsResponseTO,
  GetNewsStreamItemsRequestTO,
  GetNewsStreamItemsResponseTO,
  MapSectionTO,
  MessageEmbeddedAppTO,
} from './types';

export * from './rogerthat-errors';
export * from './rogerthat-payment';
export * from './types';

export interface RogerthatUserInfo {
  account: string;
  avatarUrl: string;
  communityId: number;
  homeScreenId: string;
  language: string;
  name: string;
  firstName: string;
  lastName: string;
}

export interface RogerthatSystem {
  os: 'ios' | 'android';
  version: string; // '10.1' (iOS) | '25' => android api 25 (Nougat)
  appVersion: string; // '2.0.2000.I'
  appName: string; // 'Rogerthat'
  appId: string; // 'rogerthat'
  colors: {
    accent: string;
    primary: string;
    primaryDark: string;
    text: string;
    textInverse: string;
  };
  debug: boolean;
  baseUrl: string;
  mainService: string;
}

export interface RogerthatMessage {
  // Returned error may be of type RogerthatMessageOpenError
  open: (messageKey: string) => Promise<void>;
}

export const enum NewsItemType {
  /**
   * application/service to person
   */
  NORMAL = 1,
  QR_CODE = 2
}

export type CameraType = 'front' | 'back';

export interface RogerthatCamera {
  // Returned error may be of type StartScanningQrCodeError
  startScanningQrCode: (cameraType: CameraType) => Promise<void>;
  // Returned error may be of type StopScanningQrCodeError
  stopScanningQrCode: (cameraType: CameraType) => Promise<void>;
}

export interface RogerthatUI {
  hideKeyboard: () => Promise<void>; // Android only
}

export interface InternetConnectionStatus {
  connected: boolean;
  connectedToWifi?: boolean;
}

interface Translations {
  /**
   * Example: { 'name': {'nl': 'Naam', 'en': 'Name'} }
   */
  [ key: string ]: { [ key: string ]: string };
}

export interface OpenParams {
  action_type?: string | null;
  action: string;
  title?: string | null;
  service?: string | null;
  params?: { [ key: string ]: any };
}

export interface RogerthatUtil {
  _translateHTML: () => void;
  _translations: { defaultLanguage: string; values: Translations };
  isConnectedToInternet: () => Promise<InternetConnectionStatus>
  open: (params: OpenParams) => Promise<void>;

  /**
   * Play a sound file which is located in the branding zip
   */
  playAudio: (path: string) => Promise<void>;
  translate: (key: string, parameters: any) => string;
  /**
   * Generate a random UUID
   */
  uuid: () => string;
}

export interface UserDetails {
  app_id: string; // 'rogerthat'
  avatar_url: string;
  email: string; // 'test@example.com
  language: string; // 'en_US'
  name: string; // 'test user'
}

export interface QrCodeScannedContent {
  status: 'resolving' | 'resolved' | 'error';
  /**
   * Content of the QR code, or an error message in case status == 'error'
   */
  content: string;
  userDetails?: UserDetails;
}

export interface RogerthatCallbacks {
  /**
   * @deprecated use rogerthat.getBadges instead
   */
  badgeUpdated: (callback: (result: RogerthatBadge) => void) => void;
  /**
   * The device its Internet connectivity has changed.
   */
  onBackendConnectivityChanged: (callback: (connectionStatus: InternetConnectionStatus) => void) => void;
  /**
   * A QR code has been scanned as result of rogerthat.camera.startScanningQrCode
   * This method is called twice. If the smartphone is connected to the Internet,
   * the app will request the details of the scanned QR code.
   * The 'userDetails' property will only be available in the second callback.
   */
  qrCodeScanned: (callback: (result: QrCodeScannedContent) => void) => void;
  /**
   * Rogerthat user and service data has been set
   */
  ready: (callback: () => void) => void;
  /**
   *  The app received an update and rogerthat.service.data is updated.
   */
  serviceDataUpdated: (callback: () => void) => void;
  /**
   * The app received an update and rogerthat.user.data is updated.
   */
  userDataUpdated: (callback: () => void) => void;
}

export interface RogerthatApiCallbacks {
  /**
   * Callbacks from service api callbacks with 'synchronous' set to false will be received here.
   * It is not necessary to use this when you always set 'synchronous' to true.
   */
  resultReceived: (callback: (method: string, result: any, error: string | null, tag: string) => void) => void;
}

export interface RogerthatApi {
  /**
   * `synchronous` makes the server execute the callback immediately instead of async with retries.
   * Setting this to true should make the result arrive much quicker, since there are less
   * round trips to the server this way.
   *
   * When synchronous is true, the result will be returned here as a promise.
   * Otherwise, the result will arrive at the callback registered at `rogerthat.api.callbacks.resultReceived`
   */
  call: (method: string, data: string | null, tag: string, synchronous: boolean) => Promise<{result: string | null} | void>;
  callbacks: RogerthatApiCallbacks;
}

export interface RogerthatMenuItem {
  label: string;
  hashedTag: string;
  action: number;
  coords: number[];
}

export interface RogerthatApp {
  exit: () => void;
  exitWithResult: (result: string) => void;
}


export const enum RogerthatContextType {
  PAY_WIDGET = 'pay',
  CREATE_PAYMENT_REQUEST = 'create-payment-request',
  PAYMENT_REQUEST = 'payment-request',
  QR_SCANNED = 'qr-scanned',
  URL = 'url',
}

export interface PayWidgetContext {
  type: RogerthatContextType.PAY_WIDGET;
  data: PayWidgetContextData;
}

export interface CreatePaymentRequestContext {
  type: RogerthatContextType.CREATE_PAYMENT_REQUEST;
  data: PaymentRequestData;
}

export interface PaymentRequestContext {
  type: RogerthatContextType.PAYMENT_REQUEST;
  data: MessageEmbeddedAppTO;
}

export interface QrScannedContext {
  type: RogerthatContextType.QR_SCANNED;
  data: { content: string };
}

/**
 * Embedded app opened via clicking an url in the browser (aka a deep link)
 */
export interface UrlContext {
  type: RogerthatContextType.URL;
  data: { content: string };
}

export type RogerthatContext = PayWidgetContext | CreatePaymentRequestContext | PaymentRequestContext | QrScannedContext | UrlContext;

export interface RogerthatBadge {
  key: 'news' | 'messages';
  count: number;
}

export interface HomeScreenContent {
  type: HomeScreenContentType;
  /**
   * Only set when \'type\' is embedded_app
   * @type {string}
   * @memberof HomeScreenContent
   */
  embedded_app?: string;
  sections?: MapSectionTO[];
  service_email?: string;
}

export enum HomeScreenContentType {
  Native = 'native',
  EmbeddedApp = 'embedded_app'
}

export class Rogerthat {
  api: RogerthatApi;
  app: RogerthatApp;
  callbacks: RogerthatCallbacks;
  camera: RogerthatCamera;
  context: () => Promise<{ context: RogerthatContext | null }>;
  /**
   * Receives a new result every time a badge is updated.
   * Replaces rogerthat.callbacks.badgeUpdated
   */
  getBadges: (resolve: (result: RogerthatBadge[]) => void, reject: (error: RogerthatError) => void) => void;
  /**
   * The menu item that was pressed to open the html app.
   */
  menuItem: RogerthatMenuItem;
  message: RogerthatMessage;
  news: {
    getNewsGroup: (request: GetNewsGroupRequestTO) => Promise<GetNewsGroupResponseTO>;
    getNewsGroups: (request: GetNewsGroupsRequestTO) => Promise<GetNewsGroupsResponseTO>;
    getNewsStreamItems: (request: GetNewsStreamItemsRequestTO) => Promise<GetNewsStreamItemsResponseTO>;
  };
  service: {
    name: string;
    account: string;
    data: any;
  };
  system: RogerthatSystem;
  ui: RogerthatUI;
  user: {
    data: { [ key: string ]: any };
    put: (data: any) => Promise<void>;
    getProfile: (resolve: (result: RogerthatUserInfo) => void, reject?: (error: string) => void) => void;
  } & RogerthatUserInfo; // accessing profile info this way is deprecated (since it isn't updated in realtime) and might be removed later
  util: RogerthatUtil;
  homeScreen: {
    getHomeScreenContent: (resolve: (result: HomeScreenContent) => void, reject: (rejectionReason: string) => void) => void;
  };
  /**
   * Only available if rogerthat-payments plugin is available in the app
   */
  payments: RogerthatPayments;
}

declare global {
  const rogerthat: Rogerthat;
  /**
   * See {@link https://github.com/emn178/js-sha256/}
   */
  const sha256: any;
}
