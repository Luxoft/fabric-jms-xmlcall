package com.luxoft.xmlcall.jms;

import com.google.protobuf.Descriptors;
import com.luxoft.xmlcall.proto.XmlCall;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;

import javax.jms.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

public final class JmsXmlCallClient
{
    final JmsTemplate jmsTemplate;
    final String ENDPOINT;
    final ConnectionFactory connectionFactory;
    final XmlCallClientHelper xmlCallClientHelper;

    public JmsXmlCallClient(ConnectionFactory connectionFactory,
                            JmsTemplate jmsTemplate,
                            final String ENDPOINT,
                            final String descriptorFileName) throws Exception {
        this.connectionFactory = connectionFactory;
        this.jmsTemplate = jmsTemplate;
        this.ENDPOINT = ENDPOINT;
        this.xmlCallClientHelper = new XmlCallClientHelper(descriptorFileName);
    }

    public String sendXMLRequest(String xmlText) {

        try {
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
                    final TextMessage message = (TextMessage) jmsTemplate.sendAndReceive(ENDPOINT,
                            session -> messageConverter.toMessage(xmlText, session));
                    return (String) jmsTemplate.getMessageConverter().fromMessage(message);
                }
            }
        }

        catch (Exception e)
        {
            throw new RuntimeException("Unable to send XML request", e);
        }
        throw new RuntimeException("Wrong sender");
    }


    public <T extends com.google.protobuf.Message>
    CompletableFuture<XmlCallResult<T>> sendRequest(Descriptors.MethodDescriptor methodDescriptor,
                                                    XmlCall.ChaincodeRequest chaincodeRequest,
                                                    com.google.protobuf.Message message,
                                                    Class<T> klass) throws Exception {
        return xmlCallClientHelper.sendRequest(methodDescriptor, chaincodeRequest, message, klass, this::sendXMLRequest);
    }

}
