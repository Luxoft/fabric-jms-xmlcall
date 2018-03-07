package com.luxoft.xmlcall.jms;

import com.luxoft.xmlcall.handler.XmlCallBlockchainConnector;
import com.luxoft.xmlcall.handler.XmlCallBlockchainConnectorFactory;
import com.luxoft.xmlcall.handler.XmlCallException;
import com.luxoft.xmlcall.handler.XmlCallHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.TextMessage;
import java.util.concurrent.CompletableFuture;

@Component
public class JmsXmlCallHandler
{
    private static final Logger logger = LoggerFactory.getLogger(JmsXmlCallHandler.class);

    @Autowired
    JmsTemplate jmsTemplate;

    private final XmlCallHandler xmlCallHandler;
    private final XmlCallBlockchainConnector xmlCallBlockchainConnector;

    public JmsXmlCallHandler(
            @Value("${descriptorFileName}") String descriptorFileName,
            @Value("${connectorClass}") String connectorClass,
            @Value("${connectorArg}") String connectorArg
    ) throws Exception {

        String currentDir = System.getProperty("user.dir");
        logger.info("Current dir: {}", currentDir);
        logger.info("Use descriptor file: '{}'", descriptorFileName);
        logger.info("Use connector: '{}', arg: '{}'", connectorClass, connectorArg);

        final XmlCallBlockchainConnectorFactory xmlCallBlockchainConnectorFactory = XmlCallBlockchainConnectorFactory.getInstance();
        xmlCallHandler = new XmlCallHandler(descriptorFileName);
        xmlCallBlockchainConnector = xmlCallBlockchainConnectorFactory.newConnection(connectorClass, connectorArg);
    }

    void exceptionHandler(TextMessage request, MessageHeaders headers, Throwable t) {
        t.printStackTrace();
        logger.error("Failed to call blockchain", t);

        Destination replyChannel = (Destination) headers.getErrorChannel();
        if (replyChannel == null) {
            try {
                replyChannel = request.getJMSReplyTo();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        }
        if (replyChannel != null) {
            String result;

            if (t instanceof XmlCallException)
                result = ((XmlCallException)t).getXmlMessage();
            else
                result = XmlCallHandler.makeError(t.getMessage());

            jmsTemplate.convertAndSend(replyChannel, result, message -> {
                message.setJMSCorrelationID(request.getJMSCorrelationID());
                return message;
            });
        }
    }

    @JmsListener(destination = "${xmlCallJmsDestination}", containerFactory = "myFactory")
    public void receiveMessage(TextMessage request, MessageHeaders headers) throws JMSException {
        final String requestText = (String) jmsTemplate.getMessageConverter().fromMessage(request);
        logger.info("Receive message: {}", requestText);

        Destination jms_replyTo = request.getJMSReplyTo();
        final String jms_correlationId = request.getJMSCorrelationID();

        try {
            final CompletableFuture<String> future = xmlCallHandler.processXmlMessage(requestText, xmlCallBlockchainConnector);

            future.thenAccept(s -> {
                if (jms_replyTo != null)
                    jmsTemplate.convertAndSend(jms_replyTo, s, message -> {
                        if (jms_correlationId != null)
                            message.setJMSCorrelationID(jms_correlationId);
                        return message;
                    });
            }).exceptionally(throwable -> {
                throwable.printStackTrace();
                exceptionHandler(request, headers, throwable);
                return null;
            });
        } catch (Exception e) {
            exceptionHandler(request, headers, e);
        }
    }

}
