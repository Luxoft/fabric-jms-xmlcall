package com.luxoft.xmlcall.handler;
import com.google.protobuf.*;
import com.googlecode.protobuf.format.XmlJavaxFormat;
import com.googlecode.protobuf.format.bits.Base64Serializer;
import com.luxoft.xmlcall.proto.XmlCall;
import com.luxoft.xmlcall.shared.ProtoLoader;
import com.luxoft.xmlcall.shared.XmlHelper;
import org.dom4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static java.lang.String.format;

public class XmlCallHandler
{
    private static final Logger logger = LoggerFactory.getLogger(XmlCallHandler.class);

    enum OperationType
    {
        QUERY, INVOKE, EVENT
    }

    class RpcMethod
    {
        final Descriptors.ServiceDescriptor serviceDescriptor;
        final Descriptors.MethodDescriptor methodDescriptor;
        final OperationType operationType;

        RpcMethod(Descriptors.MethodDescriptor methodDescriptor)
        {
            this.serviceDescriptor = methodDescriptor.getService();
            this.methodDescriptor = methodDescriptor;

            final DescriptorProtos.MethodOptions options = methodDescriptor.getOptions();
            final XmlCall.ExecType execType = options.getExtension(XmlCall.execType);

            switch (execType) {
                case UNKNOWN:
                case INVOKE:
                    operationType = OperationType.INVOKE;
                    break;
                case QUERY:
                    operationType = OperationType.QUERY;
                    break;
                case EVENT:
                    operationType = OperationType.EVENT;
                    break;
                case UNRECOGNIZED:
                default:
                    throw new RuntimeException("Unrecognized exec_type option value" + execType.getNumber());
            }
        }

        Descriptors.Descriptor getInputType()
        {
            return methodDescriptor.getInputType();
        }

        Descriptors.Descriptor getOutputType()
        {
            return methodDescriptor.getOutputType();
        }

        public XmlCallBlockchainConnector.ExecType getExecType() {
            switch (operationType)
            {
                case EVENT:
                    throw new RuntimeException("Non-invokable operation type");
                case QUERY:
                    return XmlCallBlockchainConnector.ExecType.QUERY;
                case INVOKE:
                    return XmlCallBlockchainConnector.ExecType.INVOKE;
            }

            throw new InternalError("Unhandled case " + operationType.name());
        }
    }

    private HashSet<String> services = new HashSet<>();
    private HashMap<String, RpcMethod> functions = new HashMap<>();
    private HashMap<String, RpcMethod> events = new HashMap<>();
    private final Descriptors.Descriptor chaincodeRequestType;
    private final Descriptors.Descriptor chaincodeResponceType;
    private final Descriptors.Descriptor faultType;
    private final String serviceNamespaceTag;
    private final String serviceNamespaceURI;

    private final ExtensionRegistry xmlExtensionRegistry;

    enum ServiceType
    {
        METHOD, EVENT
    }

    private RpcMethod findServiceMethod(ServiceType serviceType, String methodName)
    {
        final HashMap<String, RpcMethod> ops = serviceType == ServiceType.METHOD
                ? this.functions
                : this.events;

        return ops.computeIfAbsent(methodName, s -> {
            throw new RuntimeException(format("Unable to find %s", methodName));
        });
    }

    private static XmlJavaxFormat getFormatter()
    {
        XmlJavaxFormat xmlJavaxFormat = new XmlJavaxFormat(new Base64Serializer());
        xmlJavaxFormat.setPrintEmptyScalars(true);
        return xmlJavaxFormat;
    }

    private String makeSuccess(XmlJavaxFormat xmlFormat,
                               XmlCall.ChaincodeRequest chaincodeRequest,
                               RpcMethod rpcMethod,
                               XmlCallBlockchainConnector.Result result) throws DocumentException, InvalidProtocolBufferException {

        final DynamicMessage outputMessage = DynamicMessage.parseFrom(rpcMethod.getOutputType(), result.data);
        final XmlCall.ChaincodeResult chaincodeResult = XmlCall.ChaincodeResult.newBuilder()
                .setTxid(result.txid)
                .build();

        final Document document = DocumentHelper.parseText(xmlFormat.printToString(outputMessage));
        final Element rootElement = document.getRootElement();

        rootElement.setName(rpcMethod.getOutputType().getFullName());
        // rootElement.setName(rpcMethod.methodDescriptor.getFullName());
        XmlHelper.pasteAttributes(rootElement, chaincodeResult, XmlHelper.Dir.OUT);
        XmlHelper.changeNamespace(document, "", serviceNamespaceTag, serviceNamespaceURI);
        return document.asXML();
    }

    public static String makeError(String message) {

        final XmlCall.ChaincodeFault chaincodeFault = XmlCall.ChaincodeFault.newBuilder()
                .setMessage(message)
                .build();

        final String xmlText = getFormatter().printToString(chaincodeFault);
        Document document;
        try {
            document = DocumentHelper.parseText(xmlText);
            final Element rootElement = document.getRootElement();
            rootElement.setName(chaincodeFault.getDescriptorForType().getFullName());
        } catch (DocumentException e) {
            e.printStackTrace();
            document = DocumentHelper.createDocument();
            document.addElement(chaincodeFault.getDescriptorForType().getFullName());
        }
        return document.asXML();
    }

    private XmlCall.ChaincodeRequest
    getRequest(XmlJavaxFormat xmlFormat, String xmlString) throws IOException, XMLStreamException {
        final XmlCall.ChaincodeRequest.Builder builder = XmlCall.ChaincodeRequest.newBuilder();

        xmlFormat.merge(xmlString, xmlExtensionRegistry, builder);
        return builder.build();
    }

    private Message
    getInputMessage(XmlJavaxFormat xmlFormat, RpcMethod rpcMethod, String xmlString) throws IOException, XMLStreamException {
        final Descriptors.Descriptor inputType = rpcMethod.getInputType();
        final DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(inputType);

        xmlFormat.merge(xmlString, xmlExtensionRegistry, messageBuilder);
        return messageBuilder.build();
    }

    private String getMethodName(Element rootElement)
    {
        return rootElement.getName();
    }

    public CompletableFuture<String> processXmlMessage(String message, @Nonnull  XmlCallBlockchainConnector blockchain) throws Exception {
        final XmlJavaxFormat xmlFormat = getFormatter();

        logger.trace("Handle XML {}", message);
        final Document requestDoc = DocumentHelper.parseText(message);
        cleanupNode(requestDoc);
        logger.info("Request: {}", XmlHelper.escapeString(requestDoc.asXML()));

        // avoid using namespaces
        // todo: match namespace with services'
        if (!requestDoc.getRootElement().getNamespaceURI().isEmpty())
            XmlHelper.changeNamespace(requestDoc, requestDoc.getRootElement().getNamespaceURI(), "", "");

        final Element rootElement = requestDoc.getRootElement();
        final String methodName = getMethodName(rootElement);

        logger.trace("Lookup method {}", methodName);
        RpcMethod rpcMethod = findServiceMethod(ServiceType.METHOD, methodName);

        final XmlCall.ChaincodeRequest.Builder chaincodeRequestBuilder = XmlCall.ChaincodeRequest.newBuilder();
        final Descriptors.Descriptor inputType = rpcMethod.getInputType();
        final DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(inputType);

        XmlHelper.loadAttributes(chaincodeRequestBuilder, rootElement, XmlHelper.Dir.IN);
        XmlHelper.cleanAttributes(rootElement);
        rootElement.setName(rpcMethod.getInputType().getName());
        final String s = XmlHelper.asXML(rootElement);
        xmlFormat.merge(s, xmlExtensionRegistry, messageBuilder);

        final XmlCall.ChaincodeRequest chaincodeId = chaincodeRequestBuilder.build();
        final Message inputMessage = messageBuilder.build();

        final byte[] inputMessageBytes = inputMessage.toByteArray();
        final byte[][] args = {inputMessageBytes};

        final CompletableFuture<XmlCallBlockchainConnector.Result> exec
                = blockchain.exec(
                rpcMethod.getExecType(),
                chaincodeId.getChannel(),
                chaincodeId.getChaincodeId(),
                rpcMethod.serviceDescriptor.getName(),
                rpcMethod.methodDescriptor.getName(),
                args);

        return exec.thenApply(result -> {
            try {
                final String text = makeSuccess(xmlFormat, chaincodeId, rpcMethod, result);
                logger.info("result: {}", XmlHelper.escapeString(text));
                return text;
            } catch (Exception e) {
                throw new XmlCallException("Unable to parse result", makeError(e.getMessage()), e);
            }
        });
    }

    private static Pattern whitespace = Pattern.compile("^[ \n\t\r]+$");
    private Element cleanupElement(Element element)
    {
        cleanupNode(element);
        return element;
    }

    private void cleanupNode(Node node)
    {
        if (node instanceof Branch) {
            Branch branch = (Branch) node;

            final String text = node.getText();
            if (branch.nodeCount() > 0 && whitespace.matcher(text).matches())
                node.setText("");

            final Iterator<Node> nodeIterator = branch.nodeIterator();
            while (nodeIterator.hasNext()) {
                Node next = nodeIterator.next();
                cleanupNode(next);
            }
        }
    }

    public XmlCallHandler(String fileName) throws Exception {
        this(new ProtoLoader(fileName));
    }

    public XmlCallHandler(ProtoLoader protoLoader) throws Exception {
        Set<Descriptors.ServiceDescriptor> serviceDescriptorSet = new HashSet<>();

        this.xmlExtensionRegistry = protoLoader.getExtensionRegistry();
        this.chaincodeRequestType = protoLoader.getRequestAttributes();
        this.chaincodeResponceType = protoLoader.getResultAttributes();
        this.faultType = protoLoader.getFaultType();
        this.serviceNamespaceURI = protoLoader.getNamespaceURI();
        this.serviceNamespaceTag = serviceNamespaceURI == null || serviceNamespaceURI.isEmpty() ? "" : "tns";

        for (Descriptors.ServiceDescriptor serviceDescriptor : protoLoader.getServices()) {
            this.services.add(serviceDescriptor.getName());
            serviceDescriptorSet.add(serviceDescriptor);

            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                final RpcMethod rpcMethod = new RpcMethod(methodDescriptor);
                final String fullName = methodDescriptor.getFullName();
                switch (rpcMethod.operationType) {
                case QUERY:
                case INVOKE:
                    functions.put(fullName, rpcMethod);
                    break;

                case EVENT:
                    events.put(fullName, rpcMethod);
                    break;
                }
            }
        }
    }
}
