package com.luxoft.xmlcall.handler;

import java.util.concurrent.CompletableFuture;

public interface XmlCallBlockchainConnector
{
    enum ExecType
    {
        QUERY, INVOKE
    }

    CompletableFuture<byte[]> exec(ExecType execType,
                                   String channel,
                                   String chaincode,
                                   String chaincodeId,
                                   String method,
                                   byte[][] args) throws Exception;
}
