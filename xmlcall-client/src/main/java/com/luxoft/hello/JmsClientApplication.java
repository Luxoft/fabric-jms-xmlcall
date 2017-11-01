package com.luxoft.hello;
import javax.jms.ConnectionFactory;

import com.luxoft.xmlcall.jms.JmsXmlCallClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.SimpleMessageConverter;

@SpringBootApplication
@EnableJms
public class JmsClientApplication {

    @Bean
    public JmsListenerContainerFactory<?> myFactory(ConnectionFactory connectionFactory,
                                                    DefaultJmsListenerContainerFactoryConfigurer configurer) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        // This provides all boot's default to this factory, including the message converter
        configurer.configure(factory, connectionFactory);
        // You could still override some of Boot's default if necessary.
        return factory;
    }

    @Bean // Serialize message content to json using TextMessage
    public MessageConverter jacksonJmsMessageConverter() {
        return new SimpleMessageConverter();
//        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
//        converter.setTargetType(MessageType.TEXT);
//        converter.setTypeIdPropertyName("_type");
//        return converter;
    }

    public static void main(String[] args) throws Exception {
        // Launch the application
        ConfigurableApplicationContext context = SpringApplication.run(JmsClientApplication.class, args);

        final String ENDPOINT = context.getEnvironment().getProperty("xmlCallJmsDestination");
        final String descriptorFileName = context.getEnvironment().getProperty("descriptorFileName");

        JmsTemplate jmsTemplate = context.getBean(JmsTemplate.class);

        final JmsXmlCallClient jmsXmlCallClient = new JmsXmlCallClient(null, jmsTemplate, ENDPOINT, descriptorFileName);

        final String xmlText =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<Accumulator.GetAccumulator" +
                        "  in.chaincodeId=\"accumulator\"" +
                        "  in.channel=\"umr-2017\">" +
                        "  <memberId>USER1</memberId>" +
                        "  <accumulatorId>In_Network_Individual_Deductible</accumulatorId>" +
                        "  <planYear>2017</planYear>" +
                        "</Accumulator.GetAccumulator>";

        final String result = jmsXmlCallClient.sendXMLRequest(xmlText);
        System.out.println(result);
    }
}
