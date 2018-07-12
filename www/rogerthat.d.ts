/**
 * See http://www.rogerthat.net/developers/javascript-api for more info
 */
import {
  RogerthatError,
  RogerthatMessageOpenError,
  StartScanningQrCodeError,
  StopScanningQrCodeError,
} from './rogerthat-errors';
import { RogerthatPayments } from './rogerthat-payment';

export * from './rogerthat-errors';
export * from './rogerthat-payment';


export interface SignatureData {
  data: string;
}

export interface RogerthatUserInfo {
  account: string;
  avatarUrl: string;
  data: any;
  language: string;
  name: string;
  put: (data: any) => void;
}

export interface RogerthatSystem {
  os: 'ios' | 'android';
  version: string; // '10.1' (iOS) | '25' => android api 25 (Nougat)
  appVersion: string; // '2.0.2000.I'
  appName: string; // 'Rogerthat'
  appId: string; // 'rogerthat'
}

export interface RogerthatService {
  name: string;
  account: string;
  data: any;
}

export interface RogerthatMessage {
  open: (messageKey: string, successCallback: () => void,
         errorCallback: (error: RogerthatMessageOpenError) => void) => void;
}

export const enum NewsItemType {
  /**
   * application/service to person
   */
  NORMAL = 1,
  QR_CODE = 2
}

export interface NewsActionButton {
  action: string;
  caption: string;
  flow_params: string;
  id: string;
}

export interface NewsSender {
  avatar_id: number;
  email: string;
  name: string;
}

export interface NewsItem {
  buttons: NewsActionButton[];
  sender: NewsSender;
  broadcast_type: string;
  flags: number;
  id: number;
  image_url: string;
  message: string;
  qr_code_caption: string;
  qr_code_content: string;
  reach: number;
  sort_priority: number;
  sort_timestamp: number;
  timestamp: number;
  title: string;
  type: NewsItemType;
  users_that_rogered: string[];
  version: number;
  read: boolean;
  pinned: boolean;
  rogered: boolean;
  disabled: boolean;
  isPartial: boolean;
  sortKey: number;
}

export interface CountNewsItemsResult {
  count: number;
}

export interface ListNewsItemsParams {
  cursor?: string;
  /**
   * Amount of news items that will be returned
   */
  limit?: number;
}

export interface ListNewsItemsResult {
  items: NewsItem[];
  cursor: string;
}

export interface RogerthatNews {
  /**
   * Count news items
   */
  count: (successCallback: (result: CountNewsItemsResult) => void,
          errorCallback: (result: RogerthatError) => void) => void;
  /**
   * Get the details of a news item.
   */
  get: (successCallback: (result: NewsItem) => void,
        errorCallback: (result: RogerthatError) => void,
        params: { news_id: number }) => void;
  /**
   *  List news items for all or 1 service.
   */
  list: (successCallback: (result: ListNewsItemsResult) => void,
         errorCallback: (result: RogerthatError) => void,
         params: ListNewsItemsParams) => void;
}


export type CameraType = 'front' | 'back';

export interface RogerthatCamera {
  startScanningQrCode: (cameraType: CameraType,
                        successCallback: () => void,
                        errorCallback: (error: StartScanningQrCodeError) => void) => void;
  stopScanningQrCode: (cameraType: CameraType,
                       successCallback: () => void,
                       errorCallback: (error: StopScanningQrCodeError) => void) => void;
}

export interface PublicKey {
  algorithm: string;
  name: string;
  index: string;
  public_key: string;
}

export interface HasKeyPairResult {
  exists: boolean;
}

export interface ListAddressesResult {
  index: number;
  address: string;
}

export interface CryptoSeed {
  seed: string;
}

export interface CryptoAddress {
  address: string;
}

export interface CryptoSignature {
  /**
   * Base64 payload
   */
  payload: string;
  /**
   * Base64 signature
   */
  payload_signature: string;
}

export interface CryptoTransactionInput {
  parent_id: string;
  timelock: number;
}

export interface CryptoTransactionOutput {
  value: string;
  unlockhash: string;
}

export interface CryptoTransactionData {
  input: CryptoTransactionInput;
  outputs: CryptoTransactionOutput[];
  algorithm: SupportedAlgorithms;
  public_key: string;
  public_key_index: number;
  /**
   * base64
   */
  signature_hash: string;
  /**
   * base64 signature, this should be set by using rogerthat.security.sign by the client
   */
  signature: string;
  timelock: number;
}

export interface CryptoTransaction {
  data: CryptoTransactionData[];
  from_address: string;
  minerfees: string;
  to_address: string;
}

export interface VerifyResult {
  valid: boolean;
}

export type SupportedAlgorithms = 'ed25519';

export interface KeyPair {
  algorithm: SupportedAlgorithms;
  name: string;
  arbitrary_data: string | null;
}

export interface KeyPairList {
  keyPairs: KeyPair[];
}

export interface RogerthatSecurity {
  createKeyPair: (successCallback: (result: PublicKey) => void,
                  errorCallback: (error: RogerthatError) => void,
                  algorithm: SupportedAlgorithms,
                  keyName: string,
                  message: string | null,
                  force: boolean,
                  seed: string) => void;
  getAddress: (successCallback: (result: CryptoAddress) => void,
               errorCallback: (error: RogerthatError) => void,
               algorithm: SupportedAlgorithms,
               keyName: string,
               index: number,
               message: string | null) => void;
  getPublicKey: (successCallback: (result: PublicKey) => void,
                 errorCallback: (error: RogerthatError) => void,
                 algorithm: SupportedAlgorithms,
                 keyName: string) => void;
  getSeed: (successCallback: (result: CryptoSeed) => void,
            errorCallback: (error: RogerthatError) => void,
            algorithm: SupportedAlgorithms,
            keyName: string,
            message: string) => void;
  hasKeyPair: (successCallback: (result: HasKeyPairResult) => void,
               errorCallback: (error: RogerthatError) => void,
               algorithm: SupportedAlgorithms,
               keyName: string,
               index: number) => void;
  listAddresses: (successCallback: (result: ListAddressesResult[]) => void,
                  errorCallback: (error: RogerthatError) => void,
                  algorithm: SupportedAlgorithms,
                  keyName: string,
                  index: number) => void;
  sign: (successCallback: (result: CryptoSignature) => void,
         errorCallback: (error: RogerthatError) => void,
         algorithm: SupportedAlgorithms,
         keyName: string,
         index: number,
         message: string,
         payload: string,
         forcePin: boolean,
         hashPayload: boolean) => void;
  verify: (successCallback: (result: VerifyResult) => void,
           errorCallback: (error: RogerthatError) => void,
           algorithm: SupportedAlgorithms,
           keyName: string,
           index: number,
           payload: string,
           payload_signature: string) => void;
 listKeyPairs: (successCallback: (result: KeyPairList) => void,
                errorCallback: (error: RogerthatError) => void) => void;

}

export const enum FeatureSupported {
  CHECKING = 0,
  SUPPORTED = 1,
  NON_SUPPORTED = 2,
}

export interface RogerthatFeatures {
  base64URI: FeatureSupported;
  backgroundSize: FeatureSupported;
  callback: (feature: 'base64URI' | 'backgroundSize') => void;
}

export interface RogerthatUI {
  hideKeyboard: () => void; // Android only
}

export interface InternetConnectionStatus {
  connected: boolean;
  connectedToWifi?: boolean;
}

interface Translations {
  /**
   * Example: { 'name': {'nl': 'Naam', 'en': 'Name'} }
   */
  [key: string]: { [key: string]: string };
}

export interface RogerthatUtil {
  _translateHTML: () => void;
  _translations: { defaultLanguage: string; values: Translations };
  embeddedAppTranslations: (successCallback: (translations: { translations: any }) => void,
                            errorCallback: (error: RogerthatError) => void) => void;
  isConnectedToInternet: (callback: (connectionStatus: InternetConnectionStatus) => void) => void;
  open: (params: any, successCallback: () => void, errorCallback: () => void) => void;

  /**
   * Play a sound file which is located in the branding zip
   */
  playAudio: (path: string, callback: () => void) => void;
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
  public_key: string | null;
  public_keys: PublicKey[];
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
  badgeUpdated: (callback: (result: { key: string, count: number }) => void) => void;
  newsReceived: (callback: (result: { ids: number[] }) => void) => void;
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
  resultReceived: (callback: (method: string, result: any, error: string | null, tag: string) => void) => void;
}

export interface RogerthatApi {
  call: (method: string, data: string | null, tag: string) => void;
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

export class Rogerthat {
  api: RogerthatApi;
  app: RogerthatApp;
  callbacks: RogerthatCallbacks;
  camera: RogerthatCamera;
  context: (successCallback: (result: any) => void,
            errorCallback: (error: RogerthatError) => void) => void;
  features: RogerthatFeatures;
  /**
   * The menu item that was pressed to open the html app.
   */
  menuItem: RogerthatMenuItem;
  message: RogerthatMessage;
  news: RogerthatNews;
  service: RogerthatService;
  security: RogerthatSecurity;
  system: RogerthatSystem;
  ui: RogerthatUI;
  user: RogerthatUserInfo;
  util: RogerthatUtil;
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
