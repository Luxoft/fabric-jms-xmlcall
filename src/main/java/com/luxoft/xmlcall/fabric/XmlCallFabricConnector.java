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
    public CompletableFuture<byte[]> exec(ExecType execType, String channel, String chaincode, String chaincodeId, String method, byte[][] args) throws Exception {
        if (method == null)
            throw new IllegalArgumentException("method is null");

        if (chaincodeId == null)
            throw new IllegalArgumentException("chaincodeId is null");

        if (channel == null)
            throw new IllegalArgumentException("channel is null");

        switch (execType) {
            case QUERY:
                return fabricConnector.query(method, chaincodeId, channel, args);
            case INVOKE:
                return fabricConnector.invoke(method, chaincodeId, channel, args)
                        .thenApply(transactionEvent -> {
                            if (transactionEvent.getTransactionActionInfoCount() == 0)
                                return new byte[0];
                            return transactionEvent.getTransactionActionInfo(0).getProposalResponsePayload();
                        });
        }
        throw new InternalError("Unhandled execType: " + execType.name());
    }
}
