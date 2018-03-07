package hello;

import com.luxoft.blockchain.*;
import com.luxoft.healthcare.TestConstants;
import com.luxoft.xmlcall.proto.XmlCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ClassUtils;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringResult;

import javax.xml.bind.JAXBElement;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class WS_XmlCallTests {

    private static final String channelId = TestConstants.channelId;
    private static final String chaincodeId = TestConstants.chaincodeId;

    Jaxb2Marshaller marshaller;
    Jaxb2Marshaller unmarshaller;
    private static final String IN_NETWORK_INDIVIDUAL_DEDUCTIBLE = "In_Network_Individual_Deductible";

    @LocalServerPort
    private int port = 0;

    Jaxb2Marshaller createJaxb2Marshaller() throws Exception {
        final Jaxb2Marshaller marshaller = new Jaxb2Marshaller();

        marshaller.setPackagesToScan(ClassUtils.getPackageName(XmlCall.ChaincodeFault.class));
        marshaller.afterPropertiesSet();

        return marshaller;
    }

    @Before
    public void init() throws Exception {
        // marshaller = createXmlBeansMarshaller();
        marshaller = createJaxb2Marshaller();
        unmarshaller = createJaxb2Marshaller();
    }

    private final String getURI()
    {
        return"http://localhost:" + port + "/blockchain";
    }

    @Test
    public void getAccumulator()
    {
        WebServiceTemplate ws = new WebServiceTemplate(marshaller, unmarshaller);
        final ObjectFactory objectFactory = new ObjectFactory();

        MainGetAccumulator mainGetAccumulator = objectFactory.createMainGetAccumulator();

        mainGetAccumulator.setMemberId("USER1");
        mainGetAccumulator.setPlanYear(2017);
        mainGetAccumulator.setAccumulatorId(IN_NETWORK_INDIVIDUAL_DEDUCTIBLE);

        mainGetAccumulator.setInChannel(channelId);
        mainGetAccumulator.setInChaincodeId(chaincodeId);

        unmarshaller.setMappedClass(MainAccumulator.class);

        StringResult result  = new StringResult();
        marshaller.marshal(objectFactory.createHealthcareGetAccumulator(mainGetAccumulator), result);

        final JAXBElement<MainGetAccumulator> request = objectFactory.createHealthcareGetAccumulator(mainGetAccumulator);

//        ws.marshalSendAndReceive(getURI(), request, message -> {
//            final Result payloadResult = message.getPayloadResult();
//            System.out.println(payloadResult.toString());
//        });
        final Object response = ws.marshalSendAndReceive(getURI(), request);
    }

//    @Test
//    public void blockchainRequest() throws JAXBException {
//        WebServiceTemplate ws = new WebServiceTemplate(/*marshaller*/);
//        BlockchainRequest request = new BlockchainRequest();
//        request.setName("Spain");
//        final String uri = "http://localhost:" + port + "/blockchain";
//        final Source source = request.toSource();
//        final Result result = new StringResult();
//        assertTrue(ws.sendSourceAndReceiveToResult(uri, source, result));
//        System.out.println("Result: " + result);
//    }
}