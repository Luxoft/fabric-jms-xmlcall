import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.ExtensionRegistry;
import com.googlecode.protobuf.format.XmlFormat;
import com.luxoft.uhg.fabric.proto.ClaimAccumulator;
import com.luxoft.uhg.fabric.services.AccumulatorOuterClass;
import com.luxoft.xmlcall.jms.Application;
import com.luxoft.xmlcall.proto.XmlCall;
import com.luxoft.xmlcall.shared.Strings;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
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

import javax.jms.*;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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

    private static final String RequestSuffix = Strings.RequestSuffix;
    private static final String ResponseSuffix = Strings.ResponceSuffix;

    private String makeCallXML(Descriptors.MethodDescriptor methodDescriptor,
                            XmlCall.ChaincodeRequest chaincodeRequest,
                            com.google.protobuf.Message message) throws DocumentException {
        final XmlFormat xmlFormat = new XmlFormat();

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


    private String asXML(Element element)
    {
        OutputFormat format = new OutputFormat();
        format.setEncoding(StandardCharsets.UTF_8.displayName());

        format.setExpandEmptyElements(true);
        try {
            StringWriter out = new StringWriter();
            XMLWriter writer = new XMLWriter(out, format);

            writer.write(element);
            writer.flush();

            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException("IOException while generating textual "
                    + "representation: " + e.getMessage());
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
    Result<T> parseResponceXML(String xmlText, Class<T> klass) throws DocumentException, IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
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
        assert rootElement.getName().endsWith(ResponseSuffix);

        final XmlCall.ChaincodeResult.Builder chaincodeResultBuilder = XmlCall.ChaincodeResult.newBuilder();
        final Method newBuilder = klass.getMethod("newBuilder");
        final com.google.protobuf.Message.Builder resultBuilder = (com.google.protobuf.Message.Builder) newBuilder.invoke(null);

        final Element chaincodeResultElement = rootElement.element(chaincodeResultBuilder.getDescriptorForType().getName());
        final Element resultElement = rootElement.element(resultBuilder.getDescriptorForType().getName());

        xmlFormat.merge(asXML(chaincodeResultElement), emptyRegistry, chaincodeResultBuilder);

        // XmlFormat cannot parse self-closed tags :(
        final String resultXML = asXML(resultElement);
        // final String resultXML = resultElement.asXML();
        xmlFormat.merge(resultXML, emptyRegistry, resultBuilder);

        return new Result(chaincodeResultBuilder.build(), resultBuilder.build());
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
        final String reply = sendRequestXML(req);
        return parseResponceXML(reply, klass);
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
}
