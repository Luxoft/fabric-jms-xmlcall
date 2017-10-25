package hello;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.server.EndpointAdapter;
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

	@Bean
	public ServletRegistrationBean messageDispatcherServlet(ApplicationContext applicationContext) {
		MessageDispatcherServlet servlet = new MessageDispatcherServlet();
		servlet.setApplicationContext(applicationContext);
		servlet.setTransformWsdlLocations(true);
		return new ServletRegistrationBean(servlet, "/blockchain/*");
	}

	@Bean
	public XsdSchema blockchainSchema() {
		if (xsdSchema.isEmpty())
			return null;
		Resource resource;

		final int pos = xsdSchema.indexOf("://");
		if (pos < 0)
			resource = new FileSystemResource(xsdSchema);
		else if ("resource".equals(xsdSchema.substring(0, pos)))
			resource = new ClassPathResource(xsdSchema.substring(pos+1));
		else
			resource = new FileSystemResource(xsdSchema.substring(pos+1));

		return new SimpleXsdSchema(resource);
	}

	@Bean
	public XmlCallProvider xmlCallProvider()
	{
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
