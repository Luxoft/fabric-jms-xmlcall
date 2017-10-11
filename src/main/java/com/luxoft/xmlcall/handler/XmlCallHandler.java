package com.luxoft.xmlcall.handler;
import com.google.protobuf.*;
import com.googlecode.protobuf.format.XmlFormat;
import com.luxoft.xmlcall.proto.XmlCall;
import com.luxoft.xmlcall.shared.ProtoLoader;
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
        final Descriptors.MethodDescriptor methodDescriptor;
        final OperationType operationType;

        RpcMethod(Descriptors.MethodDescriptor methodDescriptor)
        {
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

    private final ExtensionRegistry xmlExtensionRegistry;

    enum SericeType
    {
        METHOD, EVENT
    }

    private static String getFullName(String serviceName, String methodName)
    {
        return serviceName + ":" + methodName;
    }

    private RpcMethod findServiceMethod(SericeType sericeType, String serviceName, String methodName)
    {
        final HashMap<String, RpcMethod> ops = sericeType == SericeType.METHOD
                ? this.functions
                : this.events;

        final String fullName = getFullName(serviceName, methodName);
        return ops.computeIfAbsent(fullName, s -> {
            throw new RuntimeException(format("Unable to find %s.%s", serviceName, methodName));
        });
    }

    public String makeSuccess(String channel, String chaincodeName, String methodName, String chaincodeInstance, String xmlResult) throws DocumentException {
        final Document replyDoc = DocumentHelper.createDocument();
        final Element root = replyDoc.addElement("reply");
        root
                .addAttribute("channel", channel)
                .addAttribute("chaincode", chaincodeName)
                .addAttribute("method", methodName)
                .addAttribute("id", chaincodeInstance)
                .addAttribute("outcome", "SUCCESS")
                .add(cleanupElement(DocumentHelper.parseText(xmlResult).getRootElement()));

        return replyDoc.asXML();
    }

    public static String makeError(String message) {
        return makeError(null, null, null, null, message);
    }

    public static String makeError(String channel, String chaincodeName, String methodName, String chaincodeInstance, String message) {
        final Document replyDoc = DocumentHelper.createDocument();
        final Element root = replyDoc.addElement("reply");
        if (channel != null)
            root.addAttribute("channel", channel);

        if (chaincodeName != null)
            root.addAttribute("chaincode", chaincodeName);
        if (methodName != null)
            root.addAttribute("method", methodName);
        if (chaincodeInstance != null)
            root.addAttribute("id", chaincodeInstance);

        root.addAttribute("outcome", "ERROR")
                .addText(message);
        return replyDoc.asXML();
    }

    public CompletableFuture<String> processXmlMessage(String message, @Nonnull  XmlCallBlockchainConnector blockchain) throws Exception {
        final XmlFormat xmlFormat = new XmlFormat();

        logger.trace("Handle XML {}", message);
        final Document requestDoc = DocumentHelper.parseText(message);
        cleanupNode(requestDoc);
        logger.info("Request: {}", requestDoc.asXML());

        final Element rootElement = requestDoc.getRootElement();
        final String chaincodeName = rootElement.getName();

        if (!services.contains(chaincodeName))
            throw new RuntimeException(format("Service %s is unknown", chaincodeName));
        final String chaincodeInstance = rootElement.attributeValue("id");
        final String channelName = rootElement.attributeValue("channel");

        if (rootElement.elements().size() != 1)
            throw new RuntimeException("Too many elements in query");

        final Element element = rootElement.elements().get(0);
        final String xmlInput = element.asXML();
        final String methodName = element.getName();

        logger.trace("Lookup method {}.{}", chaincodeName, methodName);
        RpcMethod rpcMethod = findServiceMethod(SericeType.METHOD, chaincodeName, methodName);
        final Descriptors.Descriptor inputType = rpcMethod.getInputType();

        final DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(inputType);

        logger.trace("Parse input params {}", xmlInput);
        xmlFormat.merge(xmlInput, xmlExtensionRegistry, messageBuilder);

        final DynamicMessage inputMessage = messageBuilder.build();
        final byte[] inputMessageBytes = inputMessage.toByteArray();
        final byte[][] args = {inputMessageBytes};

        final CompletableFuture<byte[]> exec = blockchain.exec(rpcMethod.getExecType(),
                channelName, chaincodeName, chaincodeInstance, methodName, args);

        return exec.thenApply(bytes -> {
            try {
                final DynamicMessage outputMessage = DynamicMessage.parseFrom(rpcMethod.getOutputType(), bytes);
                final String outputString = xmlFormat.printToString(outputMessage);
                final String text = makeSuccess(channelName, chaincodeName, methodName, chaincodeInstance, outputString);
                logger.info("result: {}", text);
                return text;
            } catch (Exception e) {
                throw new XmlCallException("Unable to parse result", makeError(channelName, chaincodeName, methodName, chaincodeInstance, e.getMessage()), e);
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
        for (Descriptors.ServiceDescriptor serviceDescriptor : protoLoader.getServices()) {
            this.services.add(serviceDescriptor.getName());

            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                final RpcMethod rpcMethod = new RpcMethod(methodDescriptor);
                final String fullName = getFullName(serviceDescriptor.getName(), methodDescriptor.getName());
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
