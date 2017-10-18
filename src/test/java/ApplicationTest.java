import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.ExtensionRegistry;
import com.googlecode.protobuf.format.XmlFormat;
import com.luxoft.uhg.fabric.proto.ClaimAccumulator;
import com.luxoft.uhg.fabric.services.AccumulatorOuterClass;
import com.luxoft.xmlcall.jms.Application;
import com.luxoft.xmlcall.proto.XmlCall;
import com.luxoft.xmlcall.shared.XmlHelper;
import org.dom4j.*;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.jdom2.JDOMException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import org.xml.sax.*;

import javax.jms.*;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@SpringBootConfiguration
public class ApplicationTest
{

    @Autowired
    JmsTemplate jmsTemplate;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Value("${xmlCallJmsDestination}")
    private String ENDPOINT;

    @Bean
    public DefaultMessageListenerContainer jmsContainer() {
        DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        //container.setDestinationName(destination);
        //container.setMessageListener(messageListener);

        container.start();
        return container;
    }

    private static final boolean useMethodSpecificMessages = false;
    private static final String RequestSuffix = XmlHelper.RequestSuffix;
    private static final String ResponseSuffix = XmlHelper.ResponseSuffix;

    private String makeCallXML(Descriptors.MethodDescriptor methodDescriptor,
                            XmlCall.ChaincodeRequest chaincodeRequest,
                            com.google.protobuf.Message message) throws DocumentException {
        final XmlFormat xmlFormat = new XmlFormat();

        if (useMethodSpecificMessages) {
            final String req = xmlFormat.printToString(chaincodeRequest);
            final String data = xmlFormat.printToString(message);

            final Document document = DocumentHelper.createDocument();
            final Element rootElement = document.addElement(methodDescriptor.getFullName() + RequestSuffix);

            // strip top-level element
            final Element reqElement = DocumentHelper.parseText(req).getRootElement().createCopy();
            final Element dataElement = DocumentHelper.parseText(data).getRootElement().createCopy();

            rootElement.add(reqElement);
            rootElement.add(dataElement);

            return rootElement.asXML();
        }

        else {
            final Document document = DocumentHelper.parseText(xmlFormat.printToString(message));
            final Element rootElement = document.getRootElement();

            XmlHelper.pasteAttributes(rootElement, chaincodeRequest, XmlHelper.Dir.IN);
            rootElement.setName(methodDescriptor.getFullName());
            rootElement.addAttribute("xmlns",
                    "http://www.luxoft.com/xsd/" + methodDescriptor.getFullName());
            return XmlHelper.asXML(rootElement);
        }
    }


    static class Result<T extends com.google.protobuf.Message>
    {
        final XmlCall.ChaincodeResult chaincodeResult;
        final T data;

        Result(XmlCall.ChaincodeResult chaincodeResult, T data) {
            this.chaincodeResult = chaincodeResult;
            this.data = data;
        }
    }

    private <T extends com.google.protobuf.Message>
    Result<T> parseResponseXML(String xmlText, Class<T> klass) throws DocumentException, IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final XmlFormat xmlFormat = new XmlFormat();
        final ExtensionRegistry emptyRegistry = ExtensionRegistry.getEmptyRegistry();
        final Document replyDoc = DocumentHelper.parseText(xmlText);
        final Element rootElement = replyDoc.getRootElement();
        final String rootElementName = rootElement.getName();
        if (XmlCall.ChaincodeFault.getDescriptor().getName().equals(rootElementName)) {
            final XmlCall.ChaincodeFault.Builder builder = XmlCall.ChaincodeFault.newBuilder();
            xmlFormat.merge(xmlText, emptyRegistry, builder);
            final XmlCall.ChaincodeFault chaincodeFault = builder.build();
            throw new RuntimeException(chaincodeFault.getMessage());
        }

        // success
        final XmlCall.ChaincodeResult.Builder chaincodeResultBuilder = XmlCall.ChaincodeResult.newBuilder();
        final Method newBuilder = klass.getMethod("newBuilder");
        final com.google.protobuf.Message.Builder resultBuilder = (com.google.protobuf.Message.Builder) newBuilder.invoke(null);

        if (useMethodSpecificMessages) {
            assert rootElement.getName().endsWith(ResponseSuffix);

            final Element chaincodeResultElement = rootElement.element(chaincodeResultBuilder.getDescriptorForType().getName());
            final Element resultElement = rootElement.element(resultBuilder.getDescriptorForType().getName());

            xmlFormat.merge(XmlHelper.asXML(chaincodeResultElement), emptyRegistry, chaincodeResultBuilder);

            // XmlFormat cannot parse self-closed tags :(
            final String resultXML = XmlHelper.asXML(resultElement);
            // final String resultXML = resultElement.asXML();
            xmlFormat.merge(resultXML, emptyRegistry, resultBuilder);
        }

        else {
            XmlHelper.loadAttributes(chaincodeResultBuilder, rootElement, XmlHelper.Dir.OUT);
            XmlHelper.cleanAttributes(rootElement);
            rootElement.setName(resultBuilder.getDescriptorForType().getName());
            final String s = XmlHelper.asXML(rootElement);
            xmlFormat.merge(s, emptyRegistry, resultBuilder);
        }

        @SuppressWarnings("unchecked") T data = (T)resultBuilder.build();
        return new Result<>(chaincodeResultBuilder.build(), data);
    }

    private String sendRequestXML(String xmlText) throws Exception {

        switch (1) {
            case 0: { // asynchronous reply
                jmsTemplate.setDefaultDestinationName(ENDPOINT);

                // prepare to receive reply
                final Connection connection = connectionFactory.createConnection();
                final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                final TemporaryQueue temporaryQueue = session.createTemporaryQueue();

                final String correlationId = UUID.randomUUID().toString();
                final String filter = format("JMSCorrelationID = '%s'", correlationId);

                final MessageConsumer consumer = session.createConsumer(temporaryQueue, filter);
                connection.start();
                CompletableFuture<Object> reply = new CompletableFuture<>();

                consumer.setMessageListener(message -> {
                    try {
                        reply.complete(jmsTemplate.getMessageConverter().fromMessage(message));
                    } catch (JMSException e) {
                        reply.completeExceptionally(e);
                    }
                });

                jmsTemplate.convertAndSend(xmlText, message -> {
                    message.setJMSCorrelationID(correlationId);
                    message.setJMSReplyTo(temporaryQueue);
                    return message;
                });

                final String s = (String) reply.get();
                System.out.println("OK:" + s);
            }
            break;

            case 1: { // synchronous reply
                final MessageConverter messageConverter = jmsTemplate.getMessageConverter();
                final TextMessage message = (TextMessage) jmsTemplate.sendAndReceive(ENDPOINT, session -> {
                    final Message sendMessage = messageConverter.toMessage(xmlText, session);
                    return sendMessage;
                });

                final String s = (String) jmsTemplate.getMessageConverter().fromMessage(message);
                return s;
            }
        }

        throw new RuntimeException("Wrond sender");
    }

    private <T extends com.google.protobuf.Message>
    Result<T> sendRequest(Descriptors.MethodDescriptor methodDescriptor,
                  XmlCall.ChaincodeRequest chaincodeRequest,
                  com.google.protobuf.Message message,
                  Class<T> klass) throws Exception {
        final String req = makeCallXML(methodDescriptor, chaincodeRequest, message);
        XmlHelper.xmlValidate(req, XmlHelper.fileXSDFactory("data/proto/xsd/"));
        final String reply = sendRequestXML(req);
        return parseResponseXML(reply, klass);
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

        final ClaimAccumulator.Accumulator accumulator = sendRequest(getAccumulator, chaincodeRequest, accumulatorId, ClaimAccumulator.Accumulator.class).data;

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

        sendRequest(addClaim, chaincodeRequest, addClaimRequest, Empty.class);
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
    public void xmlHandling() throws ParserConfigurationException, IOException, SAXException, JDOMException, DocumentException, VerifierConfigurationException {

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
