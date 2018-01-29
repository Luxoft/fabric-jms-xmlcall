import com.google.protobuf.Descriptors;
import com.luxoft.healthcare.TestConstants;
import com.luxoft.healthcare.messages.HealthcareMessages;
import com.luxoft.healthcare.services.HealthcareService;
import com.luxoft.xmlcall.jms.JmsServerApplication;
import com.luxoft.xmlcall.jms.JmsXmlCallClient;
import com.luxoft.xmlcall.proto.XmlCall;
import com.luxoft.xmlcall.shared.ProtoLoader;
import com.luxoft.xmlcall.shared.XmlHelper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.test.context.junit4.SpringRunner;
import rules.ActiveMQBrokerRule;

import javax.annotation.PostConstruct;
import javax.jms.*;
import java.util.function.Function;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = JmsServerApplication.class)
@SpringBootConfiguration
public class JMS_XmlCallTests
{

    private JmsXmlCallClient xmlCallClient;

    @Value("${descriptorFileName}")
    private String descriptorFileName;

    @Autowired
    JmsTemplate jmsTemplate;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Value("${xmlCallJmsDestination}")
    private String ENDPOINT;

    private static String activeMQBrokerURI = "tcp://localhost:61616";
    private String namespaceURI;
    private Function<String, String> xsdFactory;

    private static final String channelId = TestConstants.channelId;
    private static final String chaincodeId = TestConstants.chaincodeId;

    @ClassRule
    public static ActiveMQBrokerRule brokerRule = new ActiveMQBrokerRule(activeMQBrokerURI);


    @PostConstruct
    private void initialize() throws Exception {
        xmlCallClient = new JmsXmlCallClient(connectionFactory, jmsTemplate, ENDPOINT, descriptorFileName);
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

    @Before
    public void setupXmlCall() throws Exception {
        final ProtoLoader protoLoader = new ProtoLoader(descriptorFileName);
        this.namespaceURI = protoLoader.getNamespaceURI();
        this.xsdFactory = XmlHelper.inMemoryXSDFactory(protoLoader);
    }

    @Test
    public void testSend() throws Exception {

        final Descriptors.MethodDescriptor getAccumulator = HealthcareService.Healthcare.getDescriptor().findMethodByName("GetAccumulator");
        final Descriptors.MethodDescriptor addClaim = HealthcareService.Healthcare.getDescriptor().findMethodByName("AddClaim");
        final XmlCall.ChaincodeRequest chaincodeRequest = XmlCall.ChaincodeRequest.newBuilder()
                .setChannel(channelId)
                .setChaincodeId(chaincodeId)
                .build();
        final HealthcareMessages.GetAccumulator accumulatorId = HealthcareMessages.GetAccumulator.newBuilder()
                .setMemberId("USER1")
                .setPlanYear(2017)
                .setAccumulatorId("In_Network_Individual_Deductible")
                .build();

        final HealthcareMessages.Accumulator accumulator = xmlCallClient.sendRequest(getAccumulator, chaincodeRequest, accumulatorId, HealthcareMessages.Accumulator.class).get().data;

        final HealthcareMessages.AddClaim addClaimRequest = HealthcareMessages.AddClaim.newBuilder()
                .setStateHash(accumulator.getStateHash())
                .setClaim(
                        HealthcareMessages.Claim.newBuilder()
                                .setAccumulatorId(accumulatorId.getAccumulatorId())
                                .setMemberId(accumulatorId.getMemberId())
                                .setPlanYear(accumulator.getPlanYear())
                                .setSourceSystem("SRCSYS")
                                .setSourceClaimId("SRC007")
                                .setDateOfService(123123)
                                .setAmountCents(101)
                                .setPlanSponsor("ATT")
                                .build()
                ).build();

        xmlCallClient.sendRequest(addClaim, chaincodeRequest, addClaimRequest, HealthcareMessages.GetAccumulator.class).get();
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
                "<Healthcare.GetAccumulator " +
                "  xmlns=\"" +namespaceURI+ "\"" +
                "  in.channel=\"" +channelId+ "\"" +
                "  in.chaincodeId=\"" +chaincodeId+ "\"" +
                "  >\n" +
                "      <memberId>USER1</memberId>\n" +
                "      <accumulatorId>In_Network_Individual_Deductible</accumulatorId>\n" +
                "      <planYear>2017</planYear>\n" +
                "</Healthcare.GetAccumulator>\n";

        XmlHelper.xmlValidate(xmlText, xsdFactory);
    }
}
