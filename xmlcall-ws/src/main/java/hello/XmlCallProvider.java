package hello;

import com.luxoft.xmlcall.handler.XmlCallBlockchainConnector;
import com.luxoft.xmlcall.handler.XmlCallBlockchainConnectorFactory;
import com.luxoft.xmlcall.handler.XmlCallHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.ws.server.endpoint.PayloadEndpoint;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.StringSource;

import javax.annotation.PostConstruct;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import java.util.concurrent.CompletableFuture;

//@WebServiceProvider()
//@ServiceMode(Service.Mode.PAYLOAD)
//@Endpoint()
@Component
public class XmlCallProvider implements PayloadEndpoint
{

    @Autowired
    Environment environment;

    @Value("${descriptorFileName:}") String descriptorFileName;
    @Value("${connectorClass}") String connectorClass;
    @Value("${connectorArg}") String connectorArg;
    @Value("${namespacePrefix}") String namespacePrefix;


    XmlCallHandler xmlCallHandler;
    XmlCallBlockchainConnector blockchainConnector;

    public XmlCallProvider()
    {
        System.out.print(getClass().getName()+ " : created");
    }
    @PostConstruct
    void init() throws Exception {
        this.xmlCallHandler = new XmlCallHandler(descriptorFileName, namespacePrefix);
        this.blockchainConnector = XmlCallBlockchainConnectorFactory.getInstance().newConnection(connectorClass, connectorArg);

        if (blockchainConnector == null)
            throw new RuntimeException("Unable to initialize blockchain connector: " + connectorClass);
    }

    @Override
    public Source invoke(Source request) {
        try {
            final Transformer trans = TransformerFactory.newInstance().newTransformer();
            StringResult requestString = new StringResult();

            trans.transform(request, requestString);
            final CompletableFuture<String> stringCompletableFuture = xmlCallHandler.processXmlMessage(requestString.toString(), blockchainConnector);
            final String result = stringCompletableFuture.get();

            return new StringSource(result);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error in provider endpoint", e);
        }
    }
}
