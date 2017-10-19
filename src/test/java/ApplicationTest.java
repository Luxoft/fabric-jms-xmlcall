import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.luxoft.uhg.fabric.proto.ClaimAccumulator;
import com.luxoft.uhg.fabric.services.AccumulatorOuterClass;
import com.luxoft.xmlcall.jms_server.Application;
import com.luxoft.xmlcall.jms_server.JmsXmlCallClient;
import com.luxoft.xmlcall.proto.XmlCall;
import com.luxoft.xmlcall.shared.XmlHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import javax.jms.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@SpringBootConfiguration
public class ApplicationTest
{

    private JmsXmlCallClient xmlCallClient;

    @Value("${namespacePrefix}")
    private String namespacePrefix;

    @Value("${xsdPath}")
    private String xsdPath;

    @Autowired
    JmsTemplate jmsTemplate;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Value("${xmlCallJmsDestination}")
    private String ENDPOINT;

    @PostConstruct
    private void initialize()
    {
        xmlCallClient = new JmsXmlCallClient(connectionFactory, jmsTemplate, ENDPOINT, namespacePrefix, xsdPath);
    }

    @Bean
    public DefaultMessageListenerContainer jmsContainer() {
        DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        //container.setDestinationName(destination);
        //container.setMessageListener(messageListener);

        container.start();
        return container;
    }

    @Test
    public void testSend() throws Exception {

        final Descriptors.MethodDescriptor getAccumulator = AccumulatorOuterClass.Accumulator.getDescriptor().findMethodByName("GetAccumulator");
        final Descriptors.MethodDescriptor addClaim = AccumulatorOuterClass.Accumulator.getDescriptor().findMethodByName("AddClaim");
        final XmlCall.ChaincodeRequest chaincodeRequest = XmlCall.ChaincodeRequest.newBuilder()
                .setChannel("umr-2017")
                .setChaincodeId("accumulator")
                .build();
        final ClaimAccumulator.GetAccumulator accumulatorId = ClaimAccumulator.GetAccumulator.newBuilder()
                .setMemberId("USER1")
                .setPlanYear(2017)
                .setAccumulatorId("In_Network_Individual_Deductible")
                .build();

        final ClaimAccumulator.Accumulator accumulator = xmlCallClient.sendRequest(getAccumulator, chaincodeRequest, accumulatorId, ClaimAccumulator.Accumulator.class).get().data;

        final ClaimAccumulator.AddClaim addClaimRequest = ClaimAccumulator.AddClaim.newBuilder()
                .setStateHash(accumulator.getStateHash())
                .setClaim(
                        ClaimAccumulator.Claim.newBuilder()
                                .setAccumulatorId(accumulatorId.getAccumulatorId())
                                .setMemberId(accumulatorId.getMemberId())
                                .setPlanYear(accumulator.getPlanYear())
                                .setSourceSystem("UMR")
                                .setSourceClaimId("SRC007")
                                .setDateOfService(123123)
                                .setAmountCents(101)
                                .setPlanSponsor("ATT")
                                .build()
                ).build();

        xmlCallClient.sendRequest(addClaim, chaincodeRequest, addClaimRequest, Empty.class).get();
    }

    /***/
//    static org.w3c.dom.Document parseXMLString(String xmlText) throws ParserConfigurationException, IOException, SAXException {
//        return parseXMLBytes(xmlText.getBytes("utf-8"));
//    }
//
//    static org.w3c.dom.Document parseXMLBytes(byte[] xmlBytes) throws ParserConfigurationException, IOException, SAXException {
//        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
//        dbf.setNamespaceAware(true);
//        DocumentBuilder docb = dbf.newDocumentBuilder();
//
//        return docb.parse(new InputSource(new ByteArrayInputStream(xmlBytes)));
//    }
//
//    private void xmlValidate(String xmlText, String xsdPath) throws IOException, ParserConfigurationException, SAXException, DocumentException {
//        final org.w3c.dom.Document xmlDocument = parseXMLString(xmlText);
//        final org.w3c.dom.Element documentElement = xmlDocument.getDocumentElement();
//        final String nodeName = documentElement.getNodeName();
//        final org.w3c.dom.Document xsdDocument = parseXMLBytes(Files.readAllBytes(Paths.get(xsdPath, nodeName + ".xsd")));
//
//        if (documentElement.getAttribute("xmlns").isEmpty()) {
//            final String targetNamespace = xsdDocument.getDocumentElement().getAttribute("targetNamespace");
//
//            if (!targetNamespace.isEmpty())
//                xmlDocument.renameNode(documentElement, targetNamespace, nodeName);
//
//            System.out.printf("%s\n", xmlDocument.getNamespaceURI());
//        }
//
//        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
//        Source schemaFile = new DOMSource(xsdDocument);
//        Schema schema = schemaFactory.newSchema(schemaFile);
//        Validator validator = schema.newValidator();
//        validator.validate(new DOMSource(xmlDocument));
//    }

    @Test
    public void xmlHandling() throws Exception
    {

        final String xmlText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<main.GetAccumulator " +
                "  xmlns=\"http://www.luxoft.com/xsd/main.GetAccumulator\"" +
                "  in.channel=\"umr-2017\"" +
                "  in.chaincodeId=\"accumulator\"" +
                "  >\n" +
                "      <memberId>USER1</memberId>\n" +
                "      <accumulatorId>In_Network_Individual_Deductible</accumulatorId>\n" +
                "      <planYear>2017</planYear>\n" +
                "</main.GetAccumulator>\n";

        XmlHelper.xmlValidate(xmlText, XmlHelper.fileXSDFactory("data/proto/xsd/"));
    }
}
