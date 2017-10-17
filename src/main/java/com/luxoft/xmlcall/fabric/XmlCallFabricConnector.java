package com.luxoft.xmlcall.fabric;

import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.FabricConnector;
import com.luxoft.xmlcall.handler.XmlCallBlockchainConnector;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CompletableFuture;

public class XmlCallFabricConnector implements XmlCallBlockchainConnector
{
    public static XmlCallFabricConnector newInstance(String configFile) throws Exception {
        return new XmlCallFabricConnector(configFile);
    }

    final FabricConfig fabricConfig;
    final FabricConnector fabricConnector;

    public XmlCallFabricConnector(@Value("${fabricConfigFile}") String configFile) throws Exception {
        this.fabricConfig = FabricConfig.getConfigFromFile(configFile);
        this.fabricConnector = new FabricConnector(fabricConfig);
    }

    @Override
    public CompletableFuture<Result> exec(ExecType execType,
                                          String channel,
                                          String chaincodeId,
                                          String chaincodeName,
                                          String methodName,
                                          byte[][] args) throws Exception {
        if (chaincodeName == null)
            throw new IllegalArgumentException("chaincode is null");

        if (methodName == null)
            throw new IllegalArgumentException("method is null");

        if (chaincodeId == null)
            throw new IllegalArgumentException("chaincodeId is null");

        if (channel == null)
            throw new IllegalArgumentException("channel is null");

        switch (execType) {
            case QUERY:
                return fabricConnector.query(methodName, chaincodeId, channel, args)
                        .thenApply(bytes -> {
                            return new Result("", bytes);
                        });

            case INVOKE:
                return fabricConnector.invoke(methodName, chaincodeId, channel, args)
                        .thenApply(transactionEvent -> {
                            byte [] data;

                            if (transactionEvent.getTransactionActionInfoCount() == 0)
                                data = new byte[0];
                            else
                                data = transactionEvent.getTransactionActionInfo(0).getProposalResponsePayload();
                            return new Result(transactionEvent.getTransactionID(), data);
                        });
        }
        throw new InternalError("Unhandled execType: " + execType.name());
    }
}
