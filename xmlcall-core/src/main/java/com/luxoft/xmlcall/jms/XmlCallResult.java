package com.luxoft.xmlcall.jms;

import com.luxoft.xmlcall.proto.XmlCall;

public class XmlCallResult<T extends com.google.protobuf.Message>
{
    public final XmlCall.ChaincodeResult chaincodeResult;
    public final T data;

    XmlCallResult(XmlCall.ChaincodeResult chaincodeResult, T data) {
        this.chaincodeResult = chaincodeResult;
        this.data = data;
    }
}
