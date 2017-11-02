package hello;

import com.luxoft.xmlcall.shared.ProtoLoader;
import com.luxoft.xmlcall.wsdl.WSDLBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.server.endpoint.adapter.PayloadEndpointAdapter;
import org.springframework.ws.server.endpoint.mapping.UriEndpointMapping;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.util.HashMap;
import java.util.Map;

@EnableWs
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

	@Value("${xsdSchema:}") String xsdSchema;
	@Value("${descriptorFileName:}") String descriptorFileName;
	@Value("${serviceName:blockchain}") String serviceName;
	@Autowired
	ApplicationContext applicationContext;

	@Bean
	public ServletRegistrationBean messageDispatcherServlet(ApplicationContext applicationContext) {
		MessageDispatcherServlet servlet = new MessageDispatcherServlet();
		servlet.setApplicationContext(applicationContext);
		servlet.setTransformWsdlLocations(true);
		return new ServletRegistrationBean(servlet, "/" +serviceName+ "/*");
	}

	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	public ProtoLoader serviceProtoInfo() throws Exception {
		return new ProtoLoader(descriptorFileName);
	}

	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	public XsdSchema blockchainSchema() {
		final ProtoLoader protoLoader = applicationContext.getBean(ProtoLoader.class);
		final WSDLBuilder wsdlBuilder = new WSDLBuilder(protoLoader, serviceName);
		final String xmlSchema = wsdlBuilder.buildXmlSchema();
		final Resource resource = new ByteArrayResource(xmlSchema.getBytes());
		return new SimpleXsdSchema(resource);
	}

	@Bean
	public XmlCallProvider xmlCallProvider() {
		return new XmlCallProvider();
	}

//	@Bean
//	public EndpointMapping endpointMapping() throws URISyntaxException {
//		final SimpleActionEndpointMapping endpointMapping = new SimpleActionEndpointMapping();
//		final Map<String,Object> map = new HashMap<>();
//		final XmlCallProvider xmlCallProvider = new XmlCallProvider();
//
//		map.put("http://localhost/blockchain/Accumulator.AddClaim", xmlCallProvider);
//
//		endpointMapping.setActionMap(map);
//		return endpointMapping;
//	}

	@Bean
	public UriEndpointMapping endpointMapping() {
		UriEndpointMapping endpointMapping = new UriEndpointMapping();
		Map<String,Object> endpointMap = new HashMap<String,Object>() {{ put("/blockchain", "xmlCallProvider"); }};
		endpointMapping.setUsePath(true);
		endpointMapping.setEndpointMap(endpointMap);
//		endpointMapping.setInterceptors(new EndpointInterceptor[]{new SoapEnvelopeLoggingInterceptor()});
//        endpointMapping.setMessageSender(messageSender());
//        endpointMapping.setPreInterceptors(new EndpointInterceptor[] { new SoapEnvelopeLoggingInterceptor()});
		return endpointMapping;
	}

	@Bean
	public PayloadEndpointAdapter payloadEndpointAdapter()
	{
		return new PayloadEndpointAdapter();
	}
}
