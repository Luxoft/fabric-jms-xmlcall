import com.luxoft.uhg.fabric.proto.InsuredRegistry;
import com.luxoft.xmlcall.handler.XmlCallBlockchainConnector;
import com.luxoft.xmlcall.handler.XmlCallHandler;
import com.luxoft.xmlcall.reflect.XmlCallReflectionConnector;
import com.luxoft.xmlcall.shared.XmlHelper;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
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
                    String members[] = {"USER1", "USER2"};
                    final InsuredRegistry.MemberList.Builder memberListBuilder = InsuredRegistry.MemberList.newBuilder();

                    for (String member : members) {
                        final InsuredRegistry.Member m = InsuredRegistry.Member.newBuilder()
                                .setId(member)
                                .build();

                        memberListBuilder.addMembers(m);
                    }

                    return new XmlCallBlockchainConnector.Result(txid, memberListBuilder.build().toByteArray());
                }));

        XmlCallHandler xmlCallHandler = new XmlCallHandler("data/proto/services.desc");
        String request = "<InsuredRegistry.GetAllMembers in.channel=\"insuredregistry\" in.chaincodeId=\"insuredregistry\">" +
                "</InsuredRegistry.GetAllMembers>";

        final String s = xmlCallHandler.processXmlMessage(request, blockchainConnector).get();
        final String expected = "<main.MemberList xmlns=\"http://www.luxoft.com/blockchain\" out.txid=\"" +txid+ "\"><members><Id>USER1</Id></members><members><Id>USER2</Id></members></main.MemberList>";

        XMLAssert.assertXMLEqual(s, expected);
    }
}
