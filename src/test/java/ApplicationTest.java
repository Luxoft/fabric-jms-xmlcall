import com.luxoft.xmlcall.jms.Application;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private String sendFile(Path file) throws Exception {
        final String xmlText = new String(Files.readAllBytes(file));

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

    @Test
    public void testSend() throws Exception {
        final String topdir = "data/xml";
        sendFile(Paths.get(topdir, "GetAccumulator.xml"));
        sendFile(Paths.get(topdir, "AddClaim.xml"));
    }
}
