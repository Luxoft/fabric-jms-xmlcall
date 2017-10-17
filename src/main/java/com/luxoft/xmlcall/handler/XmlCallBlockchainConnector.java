package com.luxoft.xmlcall.handler;

import java.util.concurrent.CompletableFuture;

public interface XmlCallBlockchainConnector
{
    enum ExecType
    {
        QUERY, INVOKE
    }

    class Result
    {
        final String txid;
        final byte[] data;

        public Result(String txid, byte[] data) {
            this.txid = txid;
            this.data = data;
        }
    }

    CompletableFuture<Result> exec(ExecType execType,
                                   String channel,
                                   String chaincodeId,
                                   String chaincodeName,
                                   String methodName,
                                   byte[][] args) throws Exception;
}
