import com.luxoft.healthcare.messages.Healthcare;
import com.luxoft.xmlcall.handler.XmlCallBlockchainConnector;
import com.luxoft.xmlcall.handler.XmlCallHandler;
import org.custommonkey.xmlunit.XMLAssert;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

public class XmlCallTest
{

    @Test
    public void getAllMembersTest() throws Exception {
        final XmlCallBlockchainConnector blockchainConnector = mock(XmlCallBlockchainConnector.class);
        final String txid = UUID.randomUUID().toString();

        when(blockchainConnector.exec(
                anyObject(),
                anyObject(), anyObject(), anyObject(),
                anyObject(), anyObject()))
                .then(invocation -> CompletableFuture.<XmlCallBlockchainConnector.Result>supplyAsync(() -> {
                    final Healthcare.GetAccumulator getAccumulatorReply = Healthcare.GetAccumulator.newBuilder()
                            .setMemberId("USER1")
                            .setPlanYear(2017)
                            .setAccumulatorId("ACCUMULATOR")
                            .build();

                    return new XmlCallBlockchainConnector.Result(txid, getAccumulatorReply.toByteArray());
                }));

        XmlCallHandler xmlCallHandler = new XmlCallHandler("data/proto/services.desc");
        final String request =
                "<Healthcare.AddClaim in.channel=\"chanel\" in.chaincodeId=\"chaincode\">" +
                "</Healthcare.AddClaim>";

        final String s = xmlCallHandler.processXmlMessage(request, blockchainConnector).get();
        final String expected =
                "<main.GetAccumulator" +
                        " xmlns=\"http://www.luxoft.com/blockchain\" out.txid=\"" +txid+ "\">"+
                        "<memberId>USER1</memberId>"+
                        "<accumulatorId>ACCUMULATOR</accumulatorId>"+
                        "<planYear>2017</planYear>" +
                        "</main.GetAccumulator>";

        XMLAssert.assertXMLEqual(s, expected);
    }
}
