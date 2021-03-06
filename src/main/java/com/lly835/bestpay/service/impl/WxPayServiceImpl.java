package com.lly835.bestpay.service.impl;

import com.lly835.bestpay.config.SignType;
import com.lly835.bestpay.config.WxPayH5Config;
import com.lly835.bestpay.constants.WxPayConstants;
import com.lly835.bestpay.model.*;
import com.lly835.bestpay.model.wxpay.WxPayApi;
import com.lly835.bestpay.model.wxpay.request.WxPayRefundRequest;
import com.lly835.bestpay.model.wxpay.request.WxPayUnifiedorderRequest;
import com.lly835.bestpay.model.wxpay.response.WxPayAsyncResponse;
import com.lly835.bestpay.model.wxpay.response.WxPayRefundResponse;
import com.lly835.bestpay.model.wxpay.response.WxPaySyncResponse;
import com.lly835.bestpay.service.BestPayService;
import com.lly835.bestpay.utils.RandomUtil;
import com.lly835.bestpay.utils.XmlUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by 廖师兄
 * 2017-07-02 13:40
 */
@Slf4j
public class WxPayServiceImpl implements BestPayService {

    private WxPayH5Config wxPayH5Config;

    public void setWxPayH5Config(WxPayH5Config wxPayH5Config) {
        this.wxPayH5Config = wxPayH5Config;
    }


//    public void query(String id){
//        QueryRequest wxPayUnifiedorderRequest = new QueryRequest();
//        wxPayUnifiedorderRequest.setAppid(wxPayH5Config.getAppId());
//        wxPayUnifiedorderRequest.setMchId(wxPayH5Config.getMchId());
//        wxPayUnifiedorderRequest.setNonceStr(RandomUtil.getRandomStr());
//        wxPayUnifiedorderRequest.setOutTradeNo(id);
//        wxPayUnifiedorderRequest.setSign(WxPaySignature.sign(buildMap(wxPayUnifiedorderRequest), wxPayH5Config.getMchKey()));
//
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl(WxPayConstants.WXPAY_GATEWAY)
//                .addConverterFactory(SimpleXmlConverterFactory.create())
//                .build();
//        String xml = XmlUtil.toXMl(wxPayUnifiedorderRequest);
//        RequestBody body = RequestBody.create(MediaType.parse("application/xml; charset=utf-8"), xml);
//        Call<WxPaySyncResponse> call = retrofit.create(WxPayApi.class).unifiedorder(body);
//        Response<WxPaySyncResponse> retrofitResponse = null;
//        try {
//            retrofitResponse = call.execute();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }

    @Override
    public PayResponse pay(PayRequest request) {
        WxPayUnifiedorderRequest wxRequest = new WxPayUnifiedorderRequest();
        wxRequest.setOutTradeNo(request.getOrderId());
        wxRequest.setTotalFee(request.getOrderAmount());
        wxRequest.setBody(request.getOrderName());
        wxRequest.setOpenid(request.getOpenid());

        wxRequest.setTradeType("JSAPI");
        wxRequest.setAppid(wxPayH5Config.getAppId());
        wxRequest.setMchId(wxPayH5Config.getMchId());
        wxRequest.setNotifyUrl(wxPayH5Config.getNotifyUrl());
        wxRequest.setNonceStr(RandomUtil.getRandomStr());
        wxRequest.setSpbillCreateIp("8.8.8.8");
        wxRequest.setSign(WxPaySignature.sign(buildMap(wxRequest), wxPayH5Config.getMchKey()));

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(WxPayConstants.WXPAY_GATEWAY)
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .build();
        String xml = XmlUtil.toXMl(wxRequest);
        RequestBody body = RequestBody.create(MediaType.parse("application/xml; charset=utf-8"), xml);
        Call<WxPaySyncResponse> call = retrofit.create(WxPayApi.class).unifiedorder(body);
        Response<WxPaySyncResponse> retrofitResponse = null;
        try {
            retrofitResponse = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!retrofitResponse.isSuccessful()) {
            throw new RuntimeException("【微信统一支付】发起支付, 网络异常");
        }
        WxPaySyncResponse response = retrofitResponse.body();

        if (!response.getReturnCode().equals(WxPayConstants.SUCCESS)) {
            throw new RuntimeException("【微信统一支付】发起支付, returnCode != SUCCESS, returnMsg = " + response.getReturnMsg());
        }
        if (!response.getResultCode().equals(WxPayConstants.SUCCESS)) {
            throw new RuntimeException("【微信统一支付】发起支付, resultCode != SUCCESS, err_code = " + response.getErrCode() + " err_code_des=" + response.getErrCodeDes());
        }

        return buildPayResponse(response);
    }

    @Override
    public boolean verify(Map map, SignType signType, String sign) {
        return WxPaySignature.verify(map, wxPayH5Config.getMchKey());
    }

    @Override
    public PayResponse syncNotify(HttpServletRequest request) {
        return null;
    }

    /**
     * 异步通知
     *
     * @param notifyData
     * @return
     */
    @Override
    public PayResponse asyncNotify(String notifyData) {
        //xml解析为对象
        WxPayAsyncResponse asyncResponse = (WxPayAsyncResponse) XmlUtil.fromXML(notifyData, WxPayAsyncResponse.class);

        //签名校验
        if (!WxPaySignature.verify(buildMap(asyncResponse), wxPayH5Config.getMchKey())) {
            log.error("【微信支付异步通知】签名验证失败, response={}", asyncResponse.toString());
            throw new RuntimeException("【微信支付异步通知】签名验证失败");
        }

        if (!asyncResponse.getReturnCode().equals(WxPayConstants.SUCCESS)) {
            throw new RuntimeException("【微信支付异步通知】发起支付, returnCode != SUCCESS, returnMsg = " + asyncResponse.getReturnMsg());
        }
        //该订单已支付直接返回
        if (!asyncResponse.getResultCode().equals(WxPayConstants.SUCCESS)
                && "ORDERPAID".equals(asyncResponse.getErrCode())) {
            return buildPayResponse(asyncResponse);
        }

        if (!asyncResponse.getResultCode().equals(WxPayConstants.SUCCESS)) {
            throw new RuntimeException("【微信支付异步通知】发起支付, resultCode != SUCCESS, err_code = " + asyncResponse.getErrCode() + " err_code_des=" + asyncResponse.getErrCodeDes());
        }

        return buildPayResponse(asyncResponse);
    }

    /**
     * 微信退款
     *
     * @param request
     * @return
     */
    @Override
    public RefundResponse refund(RefundRequest request) {
        WxPayRefundRequest wxRequest = new WxPayRefundRequest();
        wxRequest.setOutTradeNo(request.getOrderId());
        wxRequest.setOutRefundNo(request.getOrderId());
        wxRequest.setTotalFee(request.getOrderAmount());
        wxRequest.setRefundFee(request.getOrderAmount());

        wxRequest.setAppid(wxPayH5Config.getAppId());
        wxRequest.setMchId(wxPayH5Config.getMchId());
        wxRequest.setNonceStr(RandomUtil.getRandomStr());
        wxRequest.setSign(WxPaySignature.sign(buildMap(wxRequest), wxPayH5Config.getMchKey()));

        //初始化证书
        if (wxPayH5Config.getSslContext() == null) {
            wxPayH5Config.initSSLContext();
        }
        OkHttpClient.Builder okHttpClient = new OkHttpClient()
                .newBuilder()
                .sslSocketFactory(wxPayH5Config.getSslContext().getSocketFactory());

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(WxPayConstants.WXPAY_GATEWAY)
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .client(okHttpClient.build())
                .build();
        String xml = XmlUtil.toXMl(wxRequest);
        RequestBody body = RequestBody.create(MediaType.parse("application/xml; charset=utf-8"), xml);
        Call<WxPayRefundResponse> call = retrofit.create(WxPayApi.class).refund(body);
        Response<WxPayRefundResponse> retrofitResponse = null;
        try {
            retrofitResponse = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!retrofitResponse.isSuccessful()) {
            throw new RuntimeException("【微信退款】发起退款, 网络异常");
        }
        WxPayRefundResponse response = retrofitResponse.body();

        if (!response.getReturnCode().equals(WxPayConstants.SUCCESS)) {
            throw new RuntimeException("【微信退款】发起退款, returnCode != SUCCESS, returnMsg = " + response.getReturnMsg());
        }
        if (!response.getResultCode().equals(WxPayConstants.SUCCESS)) {
            throw new RuntimeException("【微信退款】发起退款, resultCode != SUCCESS, err_code = " + response.getErrCode() + " err_code_des=" + response.getErrCodeDes());
        }

        return buildRefundResponse(response);
    }

    private RefundResponse buildRefundResponse(WxPayRefundResponse response) {
        RefundResponse refundResponse = new RefundResponse();
        refundResponse.setOrderId(response.getOutTradeNo());
        refundResponse.setOrderAmount(response.getTotalFee());
        refundResponse.setOutTradeNo(response.getTransactionId());
        refundResponse.setRefundId(response.getOutRefundNo());
        refundResponse.setOutRefundNo(response.getRefundId());
        return refundResponse;
    }

//    private Map<String,String> buildMap(QueryRequest queryRequest){
//        Map<String, String> map = new HashMap<>(64);
//        map.put("appid", queryRequest.getAppid());
//        map.put("mch_id", queryRequest.getMchId());
//        map.put("nonce_str", queryRequest.getNonceStr());
//        map.put("out_trade_no", queryRequest.getOutTradeNo());
//        return map;
//    }

    private Map<String, String> buildMap(WxPayRefundRequest request) {
        Map<String, String> map = new HashMap<>(32);
        map.put("appid", request.getAppid());
        map.put("mch_id", request.getMchId());
        map.put("nonce_str", request.getNonceStr());
        map.put("sign", request.getSign());
        map.put("sign_type", request.getSignType());
        map.put("transaction_id", request.getTransactionId());
        map.put("out_trade_no", request.getOutTradeNo());
        map.put("out_refund_no", request.getOutRefundNo());
        map.put("total_fee", String.valueOf(request.getTotalFee()));
        map.put("refund_fee", String.valueOf(request.getRefundFee()));
        map.put("refund_fee_type", request.getRefundFeeType());
        map.put("refund_desc", request.getRefundDesc());
        map.put("refund_account", request.getRefundAccount());
        return map;
    }

    private PayResponse buildPayResponse(WxPayAsyncResponse response) {
        PayResponse payResponse = new PayResponse();
        payResponse.setOrderAmount(response.getTotalFee());
        payResponse.setOrderId(response.getOutTradeNo());
        payResponse.setOutTradeNo(response.getTransactionId());
        return payResponse;
    }

    private Map<String, String> buildMap(WxPayAsyncResponse response) {
        Map<String, String> map = new HashMap<>(64);
        map.put("return_code", response.getReturnCode());
        map.put("return_msg", response.getReturnMsg());
        map.put("appid", response.getAppid());
        map.put("mch_id", response.getMchId());
        map.put("device_info", response.getDeviceInfo());
        map.put("nonce_str", response.getNonceStr());
        map.put("sign", response.getSign());
        map.put("settlement_total_fee", response.getSettlementTotalFee());
        map.put("result_code", response.getResultCode());
        map.put("err_code", response.getErrCode());
        map.put("err_code_des", response.getErrCodeDes());
        map.put("openid", response.getOpenid());
        map.put("is_subscribe", response.getIsSubscribe());
        map.put("trade_type", response.getTradeType());
        map.put("bank_type", response.getBankType());
        map.put("total_fee", String.valueOf(response.getTotalFee()));
        map.put("fee_type", response.getFeeType());
        map.put("cash_fee", response.getCashFee());
        map.put("cash_fee_type", response.getCashFeeType());
        map.put("coupon_fee", response.getCouponFee());
        map.put("coupon_id_0", response.getCouponId0());
        map.put("coupon_fee_0", response.getCouponFee0());
        map.put("coupon_type_0",response.getCouponType0());
        map.put("coupon_count", response.getCouponCount());
        map.put("transaction_id", response.getTransactionId());
        map.put("out_trade_no", response.getOutTradeNo());
        map.put("attach", response.getAttach());
        map.put("time_end", response.getTimeEnd());
        return map;
    }
    /**
     * 构造map
     *
     * @param request
     * @return
     */
    private Map<String, String> buildMap(WxPayUnifiedorderRequest request) {
        Map<String, String> map = new HashMap<>(32);
        map.put("appid", request.getAppid());
        map.put("mch_id", request.getMchId());
        map.put("nonce_str", request.getNonceStr());
        map.put("sign", request.getSign());
        map.put("attach", request.getAttach());
        map.put("body", request.getBody());
        map.put("detail", request.getDetail());
        map.put("notify_url", request.getNotifyUrl());
        map.put("openid", request.getOpenid());
        map.put("out_trade_no", request.getOutTradeNo());
        map.put("spbill_create_ip", request.getSpbillCreateIp());
        map.put("total_fee", String.valueOf(request.getTotalFee()));
        map.put("trade_type", request.getTradeType());
        return map;
    }

    /**
     * 返回给h5的参数
     *
     * @param response
     * @return
     */
    private PayResponse buildPayResponse(WxPaySyncResponse response) {
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = RandomUtil.getRandomStr();
        String packAge = "prepay_id=" + response.getPrepayId();
        String signType = "MD5";

        //先构造要签名的map
        Map<String, String> map = new HashMap<>(16);
        map.put("appId", response.getAppid());
        map.put("timeStamp", timeStamp);
        map.put("nonceStr", nonceStr);
        map.put("package", packAge);
        map.put("signType", signType);

        PayResponse payResponse = new PayResponse();
        payResponse.setAppId(response.getAppid());
        payResponse.setTimeStamp(timeStamp);
        payResponse.setNonceStr(nonceStr);
        payResponse.setPackAge(packAge);
        payResponse.setSignType(signType);
        payResponse.setPaySign(WxPaySignature.sign(map, wxPayH5Config.getMchKey()));

        return payResponse;
    }

}
