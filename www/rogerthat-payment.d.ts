// todo Everything payments-related should be in the rogerthat-payments-plugin but I don't know how
import { CryptoTransaction, RogerthatPaymentApp, SignatureData, SupportedAlgorithms, UserDetails } from './rogerthat';
import { RogerthatError } from './rogerthat-errors';

export interface RogerthatPaymentApp {
  install: (successCallback: (result: { install: boolean }) => void,
            errorCallback: (error: RogerthatError) => void) => void;
  installed: (successCallback: (result: { installed: boolean }) => void,
              errorCallback: (error: RogerthatError) => void,
              testMode: boolean) => void;
  pay: (successCallback: () => void,
        errorCallback: (error: RogerthatError) => void,
        transactionId: string,
        testMode: boolean) => void;
}

export type RequiredActionType = 'follow_url' | 'enter_code';

export interface RequiredAction {
  /**
   * Optional url to where the user should be redirected.
   */
  action: RequiredActionType;
  /**
   * Description of what the user is now supposed to do.
   */
  description: string | null;
  /**
   * JSON encoded optional data to be used dependent on the action
   */
  data: string | null;
}

export type AssetType = 'account' | 'bank' | 'creditcard' | 'cryptocurrency_wallet';

export interface CreatePaymentProviderAsset {
  type: AssetType;
  currency: string;
  /**
   * Only to be used when type == 'bank'
   */
  iban: string | null;
  /**
   * Only to be used when type == 'cryptocurrency_wallet'
   */
  address: string | null;
  /**
   * Currently only to used when type == 'cryptocurrency_wallet'
   */
  id: string | null;
}

export type ColorSchemes = 'light' | 'primary' | 'secondary' | 'danger' | 'dark';

export type PaymentProviderId = 'paycash' | 'threefold' | 'payconiq';

export class PaymentProvider {
  id: string;
  name: string;
  logo_url: string;
  version: number;
  oauth_authorize_url: string;
  /**
   * Whether or not the user has already authorized this provider.
   */
  enabled: boolean;
  /**
   * List of asset types that this provider supports.
   */
  asset_types: AssetType[];
  /**
   * List of currencies that this provider supports.
   */
  currencies: string[];
  background_color: string;
  text_color: string;
  button_color: ColorSchemes;
  black_white_logo: string;
  authorize: (successCallback: (result: string) => void, errorCallback: (error: RogerthatError) => void) => void;
  profile: (successCallback: (result: PaymentProviderProfile) => void,
            errorCallback: (error: RogerthatError) => void) => void;
  assets: (successCallback: (result: PaymentProviderAsset) => void,
           errorCallback: (error: RogerthatError) => void) => void;
  createAsset: (successCallback: (result: PaymentProviderAsset) => void,
                errorCallback: (error: RogerthatError) => void,
                asset: CreatePaymentProviderAsset) => void;
}

export interface PaymentProviderProfile {
  first_name: string;
  last_name: string;
}

export class PaymentTransaction {
  asset_id: string;
  provider_id: string;
  id: string;
  type: string;
  name: string;
  amount: number;
  currency: string;
  memo: string;
  timestamp: number;
  from_asset_id: string;
  to_asset_id: string;
}

export interface TransactionsList {
  cursor: string;
  transactions: PaymentTransaction[];
}

export interface PendingPaymentUpdate {
  status: string;
  transaction_id: string;
}

export interface CreateTransactionBaseResult extends PendingPaymentUpdate {
  provider_id: PaymentProviderId;
  success: boolean;
}

export type GetTransactionsTypes = 'confirmed' | 'pending';

export interface PaymentAssetBalance {
  amount: number;
  description: string | null;
}

export interface PaymentProviderAsset {
  available_balance: PaymentAssetBalance;
  total_balance: PaymentAssetBalance | null;
  currency: string;
  provider_id: string;
  name: string;
  verified: boolean;
  required_action: RequiredAction | null;
  enabled: boolean;
  id: string;
  type: AssetType;
  has_balance: boolean;
  has_transactions: boolean;
  transactions: (successCallback: (result: TransactionsList) => void,
                 errorCallback: (error: RogerthatError) => void,
                 cursor: string | null,
                 transactionType: GetTransactionsTypes) => void;
  verify: (successCallback: () => void, errorCallback: (error: RogerthatError) => void, cursor: string | null) => void;
  /**
   * statusCallback is called every time the status of the transaction updates.
   */
  receive: (statusCallback: (result: PendingPaymentUpdate) => void,
            errorCallback: (error: RogerthatError) => void,
            amount: number,
            memo: string | null) => void;
}

export interface PendingPayment {
  id: string;
  amount: number;
  currency: string;
  memo: string;
  timestamp: number;
  provider: PaymentProvider;
  assets: PaymentProviderAsset[];
  receiver: UserDetails;
  receiver_asset: PaymentProviderAsset;
  getSignatureData: (successCallback: (signature?: CryptoTransaction) => void,
                     errorCallback: (error: RogerthatError) => void,
                     assetId: string) => void;
  getTransactionData: (successCallback: (payload: CryptoTransaction) => void,
                       errorCallback: (error: RogerthatError) => void,
                       algorithm: SupportedAlgorithms,
                       name: string,
                       index: number,
                       signature_data: string) => void;
  confirm: (successCallback: (status: PendingPaymentUpdate) => void,
            errorCallback: (error: RogerthatError) => void,
            signature?: string | null) => void;
  cancel: (successCallback: () => void, errorCallback: (error: RogerthatError) => void) => void;
}

export interface ProviderRemovedCallbackResult {
  provider_id: string;
}

export interface AssetsUpdatedCallbackResult {
  provider_id: string;
  assets: PaymentProviderAsset[];
}

export interface PaymentMethod {
  provider_id: PaymentProviderId;
  currency: string;
  amount: number;
  precision: number;
  target: string;
}

/** @deprecated */
export interface PayWidgetData {
  t: 2;
  result_type: string;
  methods: PaymentMethod[];
  memo: string;
  target: string;
  message_key: string;
}

export const enum PayContextType {
  PAY = 'pay'
}

export interface PayContext {
  type: PayContextType;
  data: PayWidgetContextData;
}

export interface PayMethod {
  amount: number;
  currency: string;
  precision: number;
  target: string;
}

export interface PayWidgetContextData {
  test_mode: boolean;
  message_key: string;
  target: string;
  method: PayMethod;
  memo: string;
  provider: PaymentProvider;
}

export interface CreateTransactionBaseResult extends PendingPaymentUpdate {
  provider_id: PaymentProviderId;
  success: boolean;
}

export interface CreateTransactionResultInternal {
  params: string;
  transaction_id: string;
}

export interface CreateTransactionResult {
  params: CreateTransactionResultType;
  transaction_id: string;
}

export interface PayconiqCreateTransactionResult extends CreateTransactionBaseResult {
  payconic_transaction_id: string;
  provider_id: 'payconic';
  payconic_transaction_url: string;
}

export type CreateTransactionResultType = CreateTransactionBaseResult | PayconiqCreateTransactionResult;

export const enum RogerthatPaymentErrorCode {
  UNKNOWN = 'unknown',
  PROVIDER_NOT_FOUND = 'provider_not_found',
  CURRENCY_UNKNOWN = 'currency_unknown',
  PERMISSION_DENIED = 'permission_denied',
  TRANSACTION_NOT_FOUND = 'transaction_not_found',
  TRANSACTION_NOT_INITIATED = 'transaction_not_initiated',
  TRANSACTION_ALREADY_INITIATED = 'transaction_already_initiated',
  TRANSACTION_FINISHED = 'transaction_finished',
  ACCOUNT_ALREADY_EXISTS = 'account_already_exists',
  DUPLICATE_WALLET = 'duplicate_wallet',
  INVALID_IBAN = 'invalid_iban',
  INVALID_VERIFICATION_CODE = 'invalid_verification_code',
  CANNOT_VERIFY_WALLET_TYPE = 'cannot_verify_wallet_type',
  INSUFFICIENT_FUNDS = 'insufficient_funds',
}

export interface RogerthatPaymentError<T = any> {
  code: RogerthatPaymentErrorCode;
  data: T;
  message: string;
}

export class RogerthatPayments {
  apps: {
    payconiq: RogerthatPaymentApp;
  };
  assets: (successCallback: (assets: PaymentProviderAsset[]) => void,
           errorCallback: (error: RogerthatError) => void) => void;
  callbacks: {
    onProviderUpdated: (callback: (result: PaymentProvider) => void) => void;
    onProviderRemoved: (callback: (result: ProviderRemovedCallbackResult) => void) => void;
    onAssetsUpdated: (callback: (result: AssetsUpdatedCallbackResult) => void) => void;
    onAssetUpdated: (callback: (result: PaymentProviderAsset) => void) => void;
  };
  cancelPayment: (successCallback: () => void,
                  errorCallback: (error: RogerthatError) => void,
                  transactionId: string) => void;
  // result.params is json parseable to CreateTransactionResultType
  createTransaction: (successCallback: (result: CreateTransactionResultInternal) => void,
                      errorCallback: (error: RogerthatPaymentError<null | string>) => void,
                      updateCallback: (result: PendingPaymentUpdate) => void,
                      providerId: PaymentProviderId,
                      params: string) => void;
  getPendingPaymentDetails: (successCallback: (result: PendingPayment) => void,
                             errorCallback: (error: RogerthatError) => void,
                             updateCallback: (result: PendingPaymentUpdate) => void,
                             transactionId: string) => void;
  getTargetInfo: (successCallback: (paymentProviders: PaymentProvider[]) => void,
                  errorCallback: (error: RogerthatError) => void,
                  providerId: PaymentProviderId,
                  target: string,
                  currency: string) => void;
  getTransactionData: (successCallback: (payload: SignatureData) => void,
                       errorCallback: (error: RogerthatError) => void,
                       algorithm: SupportedAlgorithms,
                       name: string,
                       index: number,
                       signature_data: string) => void;
  /**
   * Lists payment providers. If the `all` parameter is true, all available payment providers are returned.
   * Otherwise, only the payment providers that the user is connected with are returned.
   * The successCallback function is also called every time the payment provider has changed.
   */
  providers: (successCallback: (paymentProviders: PaymentProvider[]) => void,
              errorCallback: (error: RogerthatError) => void,
              all?: boolean) => void;
}
