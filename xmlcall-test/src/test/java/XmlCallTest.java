import com.luxoft.xmlcall.handler.XmlCallBlockchainConnector;
import com.luxoft.xmlcall.handler.XmlCallHandler;
import com.luxoft.xmlcall.reflect.XmlCallReflectionConnector;
import com.luxoft.xmlcall.shared.XmlHelper;
import org.junit.Test;

public class XmlCallTest
{
    @Test
    public void getAllMembersTest() throws Exception {
        XmlCallBlockchainConnector cc = new XmlCallReflectionConnector("services");
        XmlCallHandler xmlCallHandler = new XmlCallHandler("data/proto/services.desc", "");
        String request = "<InsuredRegistry.GetAllMembers in.channel=\"insuredregistry\" in.chaincodeId=\"insuredregistry\">" +
                "</InsuredRegistry.GetAllMembers>";

        final String s = xmlCallHandler.processXmlMessage(request, cc).get();
        System.out.println(XmlHelper.prettyPrintXML(s));
    }
}
