package Ngaritech.com.payment_service.MpesaIntergration.Services;


import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.B2CTransactionSyncResponse;
import Ngaritech.com.payment_service.MpesaIntergration.B2C_Transaction.InternalB2CTransactionRequest;
import Ngaritech.com.payment_service.MpesaIntergration.C2B_Transaction.C2BRequest;
import Ngaritech.com.payment_service.MpesaIntergration.C2B_Transaction.C2BResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Config.MpesaConfigEntity;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.InternalStkPushRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.InternalStkPushStatusRequest;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkPushStatusResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Mpesa_Express.StkPushSyncResponse;
import Ngaritech.com.payment_service.MpesaIntergration.OAUTH_Token.AccessTokenResponse;
import Ngaritech.com.payment_service.MpesaIntergration.Register_URL.RegisterUrlResponse;

import java.io.IOException;

public interface DarajaApi {
//<<<<<<< HEAD
//    AccessTokenResponse getAccessToken();
//
//    RegisterUrlResponse registerUrl();
//    C2BResponse C2BTransaction(C2BRequest c2BRequest);
//    B2CTransactionSyncResponse b2CTransaction(InternalB2CTransactionRequest internalB2CTransactionRequest);
//    StkPushSyncResponse stkPushTransaction(InternalStkPushRequest internalStkPushRequest);
//    StkPushStatusResponse stkPushStatus(InternalStkPushStatusRequest internalStkPushStatusRequest);
//=======

    AccessTokenResponse getAccessToken(MpesaConfigEntity config);
    RegisterUrlResponse registerUrl();
    C2BResponse C2BTransaction(C2BRequest c2BRequest);
    B2CTransactionSyncResponse b2CTransaction(InternalB2CTransactionRequest internalB2CTransactionRequest);
    StkPushSyncResponse stkPushTransaction(InternalStkPushRequest internalStkPushRequest) throws IOException;

    StkPushStatusResponse stkPushStatus(InternalStkPushStatusRequest internalStkPushStatusRequest, String paymentPurpose);


}
