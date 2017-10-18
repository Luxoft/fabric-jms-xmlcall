package com.luxoft.xmlcall.handler;
import com.google.protobuf.*;
import com.googlecode.protobuf.format.ProtobufFormatter;
import com.googlecode.protobuf.format.XmlFormat;
import com.luxoft.xmlcall.proto.XmlCall;
import com.luxoft.xmlcall.shared.ProtoLoader;
import com.luxoft.xmlcall.shared.XmlHelper;
import org.dom4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

    private static final String RequestSuffix = XmlHelper.RequestSuffix;
    private static final String ResponseSuffix = XmlHelper.ResponseSuffix;
    private static final boolean useMethodSpecificMessages = false;

    private HashSet<String> services = new HashSet<>();
    private HashMap<String, RpcMethod> functions = new HashMap<>();
    private HashMap<String, RpcMethod> events = new HashMap<>();
    private final Descriptors.Descriptor chaincodeRequestType;
    private final Descriptors.Descriptor chaincodeResponceType;
    private final Descriptors.Descriptor faultType;

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

    private String makeSuccess(XmlFormat xmlFormat,
                               XmlCall.ChaincodeRequest chaincodeRequest,
                               RpcMethod rpcMethod,
                               XmlCallBlockchainConnector.Result result) throws DocumentException, InvalidProtocolBufferException {

        final DynamicMessage outputMessage = DynamicMessage.parseFrom(rpcMethod.getOutputType(), result.data);
        final XmlCall.ChaincodeResult chaincodeResult = XmlCall.ChaincodeResult.newBuilder()
                .setTxid(result.txid)
                .build();

        if (useMethodSpecificMessages) {
            final Element outputMessageElement = DocumentHelper.parseText(xmlFormat.printToString(outputMessage)).getRootElement().createCopy();
            final Element chaincodeResultElement = DocumentHelper.parseText(xmlFormat.printToString(chaincodeResult)).getRootElement().createCopy();
            final Element chaincodeRequestElement = DocumentHelper.parseText(xmlFormat.printToString(chaincodeRequest)).getRootElement().createCopy();

            final Document replyDoc = DocumentHelper.createDocument();
            final Element rootElement = replyDoc.addElement(rpcMethod.methodDescriptor.getFullName() + ResponseSuffix);
            rootElement.add(chaincodeResultElement);
            rootElement.add(chaincodeRequestElement);
            rootElement.add(outputMessageElement);
            return replyDoc.asXML();
        }

        else {
            final Document document = DocumentHelper.parseText(xmlFormat.printToString(outputMessage));
            final Element rootElement = document.getRootElement();

            rootElement.setName(rpcMethod.methodDescriptor.getFullName());
            XmlHelper.pasteAttributes(rootElement, chaincodeResult, XmlHelper.Dir.OUT);
            return document.asXML();
        }
    }

    public static String makeError(String message) {
        return makeErrorXML(message);
    }

    public static String makeErrorXML(String message) {

        final XmlCall.ChaincodeFault chaincodeFault = XmlCall.ChaincodeFault.newBuilder()
                .setMessage(message)
                .build();

        return new XmlFormat().printToString(chaincodeFault);
    }

    private XmlCall.ChaincodeRequest
    getRequest(XmlFormat xmlFormat, String xmlString) throws ProtobufFormatter.ParseException {
        final XmlCall.ChaincodeRequest.Builder builder = XmlCall.ChaincodeRequest.newBuilder();

        xmlFormat.merge(xmlString, xmlExtensionRegistry, builder);
        return builder.build();
    }

    private Message
    getInputMessage(XmlFormat xmlFormat, RpcMethod rpcMethod, String xmlString) throws ProtobufFormatter.ParseException {
        final Descriptors.Descriptor inputType = rpcMethod.getInputType();
        final DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(inputType);

        xmlFormat.merge(xmlString, xmlExtensionRegistry, messageBuilder);
        return messageBuilder.build();
    }

    private String getMethodName(Element rootElement)
    {
        String request = rootElement.getName();
        if (useMethodSpecificMessages) {
            if (!request.endsWith(RequestSuffix))
                throw new RuntimeException("name should end with " + RequestSuffix);

            return request.substring(0, request.length() - RequestSuffix.length());
        }
        else
            return request;
    }

    public CompletableFuture<String> processXmlMessage(String message, @Nonnull  XmlCallBlockchainConnector blockchain) throws Exception {
        final XmlFormat xmlFormat = new XmlFormat();

        logger.trace("Handle XML {}", message);
        final Document requestDoc = DocumentHelper.parseText(message);
        cleanupNode(requestDoc);
        logger.info("Request: {}", requestDoc.asXML());

        final Element rootElement = requestDoc.getRootElement();
        final String methodName = getMethodName(rootElement);

        logger.trace("Lookup method {}", methodName);
        RpcMethod rpcMethod = findServiceMethod(ServiceType.METHOD, methodName);

        final XmlCall.ChaincodeRequest.Builder chaincodeRequestBuilder = XmlCall.ChaincodeRequest.newBuilder();
        final Descriptors.Descriptor inputType = rpcMethod.getInputType();
        final DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(inputType);

        if (useMethodSpecificMessages) {
            final String chaincodeRequestString = rootElement.element(chaincodeRequestType.getName()).asXML();
            final String inputParamsString = rootElement.element(rpcMethod.getInputType().getName()).asXML();

            xmlFormat.merge(chaincodeRequestString, xmlExtensionRegistry, chaincodeRequestBuilder);
            xmlFormat.merge(inputParamsString, xmlExtensionRegistry, messageBuilder);
        }

        else {
            XmlHelper.loadAttributes(chaincodeRequestBuilder, rootElement, XmlHelper.Dir.IN);

            XmlHelper.cleanAttributes(rootElement);
            rootElement.setName(rpcMethod.getInputType().getName());
            final String s = XmlHelper.asXML(rootElement);
            xmlFormat.merge(s, xmlExtensionRegistry, messageBuilder);
        }

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
//                final String outputString = xmlFormat.printToString(outputMessage);

                final String text = makeSuccess(xmlFormat, chaincodeId, rpcMethod, result);
                logger.info("result: {}", text);
                return text;
            } catch (Exception e) {
                throw new XmlCallException("Unable to parse result", makeErrorXML(e.getMessage()), e);
            }
        });
    }

    private static Pattern whitespace = Pattern.compile("^[ \n\t\r]+$");
    Element cleanupElement(Element element)
    {
        cleanupNode(element);
        return element;
    }

    void cleanupNode(Node node)
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
        final ProtoLoader protoLoader = new ProtoLoader(fileName);

        this.xmlExtensionRegistry = protoLoader.getExtensionRegistry();
        this.chaincodeRequestType = protoLoader.getType("xmlcall.ChaincodeRequest");
        this.chaincodeResponceType = protoLoader.getType("xmlcall.ChaincodeResult");
        this.faultType = protoLoader.getType("xmlcall.ChaincodeFault");

        for (Descriptors.ServiceDescriptor serviceDescriptor : protoLoader.getServices()) {
            this.services.add(serviceDescriptor.getName());

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
