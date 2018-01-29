import com.luxoft.healthcare.messages.HealthcareMessages;
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
                    final HealthcareMessages.Accumulator getAccumulatorReply = HealthcareMessages.Accumulator.newBuilder()
                            .setMemberId("USER1")
                            .setPlanYear(2017)
                            .setAccumulatorId("ACCUMULATOR")
                            .setValueCents(0)
                            .build();

                    return new XmlCallBlockchainConnector.Result(txid, getAccumulatorReply.toByteArray());
                }));

        XmlCallHandler xmlCallHandler = new XmlCallHandler("data/proto/services.desc");
        final String request =
                "<Healthcare.AddClaim in.channel=\"chanel\" in.chaincodeId=\"chaincode\">" +
                "</Healthcare.AddClaim>";

        final String s = xmlCallHandler.processXmlMessage(request, blockchainConnector).get();
        final String expected =

                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<tns:main.Accumulator xmlns:tns=\"http://www.luxoft.com/blockchain\" out.txid=\"" + txid+ "\">" +
                        "<tns:memberId>USER1</tns:memberId>" +
                        "<tns:accumulatorId>ACCUMULATOR</tns:accumulatorId>" +
                        "<tns:valueCents>0</tns:valueCents>" +
                        "<tns:planYear>2017</tns:planYear>" +
                        "<tns:stateHash/>" +
                        "</tns:main.Accumulator>";

        XMLAssert.assertXMLEqual(s, expected);
    }
}
