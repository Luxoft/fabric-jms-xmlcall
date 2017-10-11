package com.luxoft.xmlcall.jms;

import com.luxoft.xmlcall.fabric.XmlCallFabricConnector;
import com.luxoft.xmlcall.handler.XmlCallBlockchainConnector;
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
import java.lang.reflect.Constructor;
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
            @Value("${fabricConfigFile}") String fabricConfigFile
    ) throws Exception {

        String currentDir = System.getProperty("user.dir");
        System.out.println("Current dir using System:" +currentDir);

        xmlCallHandler = new XmlCallHandler(descriptorFileName);
        xmlCallBlockchainConnector = createConnector(connectorClass, fabricConfigFile);
    }

    static XmlCallBlockchainConnector createConnector(String connectorClass, String connectorArg) throws Exception {
        if ("XmlCallFabricConnector".equals(connectorClass))
            return XmlCallFabricConnector.newInstance(connectorArg);

        final Class<?> aClass = Thread.currentThread().getContextClassLoader().loadClass(connectorClass);

        Constructor<?> constructor;

        try {
            constructor = aClass.getConstructor();
            return (XmlCallBlockchainConnector) constructor.newInstance();
        }

        catch (NoSuchMethodException e)
        {
        }

        try {
            constructor = aClass.getConstructor(String.class);
            return (XmlCallBlockchainConnector) constructor.newInstance(connectorArg);
        }
        catch (NoSuchMethodException e)
        {
        }

        throw new RuntimeException("Unable to fonc suitable constructor in " + connectorClass);
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
                exceptionHandler(request, headers, throwable);
                return null;
            });
        } catch (Exception e) {
            exceptionHandler(request, headers, e);
        }
    }

}
