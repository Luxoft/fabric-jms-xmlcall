package com.luxoft.xmlcall.jms_server;

import com.google.protobuf.Descriptors;
import com.google.protobuf.ExtensionRegistry;
import com.googlecode.protobuf.format.ProtobufFormatter;
import com.googlecode.protobuf.format.XmlFormat;
import com.luxoft.xmlcall.proto.XmlCall;
import com.luxoft.xmlcall.shared.XmlHelper;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class XmlCallClientHelper
{
    final String namespacePrefix;
    final String xsdPath;
    final Function<String, String> xsdFactory;

    public XmlCallClientHelper(String namespacePrefix, String xsdPath)
    {
        if (!namespacePrefix.isEmpty() && !namespacePrefix.endsWith("/"))
            namespacePrefix = namespacePrefix + "/";
        this.namespacePrefix = namespacePrefix;
        this.xsdPath = xsdPath;
        this.xsdFactory = XmlHelper.fileXSDFactory(xsdPath);
    }

    private String makeCallXML(Descriptors.MethodDescriptor methodDescriptor,
                               XmlCall.ChaincodeRequest chaincodeRequest,
                               com.google.protobuf.Message message) throws DocumentException {
        final XmlFormat xmlFormat = new XmlFormat();

        final Document document = DocumentHelper.parseText(xmlFormat.printToString(message));
        final Element rootElement = document.getRootElement();

        XmlHelper.pasteAttributes(rootElement, chaincodeRequest, XmlHelper.Dir.IN);
        rootElement.setName(methodDescriptor.getFullName());
        rootElement.addAttribute("xmlns",
                namespacePrefix + methodDescriptor.getFullName());
        return XmlHelper.asXML(rootElement);
    }


    private <T extends com.google.protobuf.Message>
    XmlCallResult<T> parseResponseXML(String xmlText, Class<T> klass) throws DocumentException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, ProtobufFormatter.ParseException {
        final XmlFormat xmlFormat = new XmlFormat();
        final ExtensionRegistry emptyRegistry = ExtensionRegistry.getEmptyRegistry();
        final Document replyDoc = DocumentHelper.parseText(xmlText);
        final Element rootElement = replyDoc.getRootElement();
        final String rootElementName = rootElement.getName();
        if (XmlCall.ChaincodeFault.getDescriptor().getName().equals(rootElementName)) {
            final XmlCall.ChaincodeFault.Builder builder = XmlCall.ChaincodeFault.newBuilder();
            xmlFormat.merge(xmlText, emptyRegistry, builder);
            final XmlCall.ChaincodeFault chaincodeFault = builder.build();
            throw new RuntimeException(chaincodeFault.getMessage());
        }

        // success
        final XmlCall.ChaincodeResult.Builder chaincodeResultBuilder = XmlCall.ChaincodeResult.newBuilder();
        final Method newBuilder = klass.getMethod("newBuilder");
        final com.google.protobuf.Message.Builder resultBuilder = (com.google.protobuf.Message.Builder) newBuilder.invoke(null);

        XmlHelper.loadAttributes(chaincodeResultBuilder, rootElement, XmlHelper.Dir.OUT);
        XmlHelper.cleanAttributes(rootElement);
        rootElement.setName(resultBuilder.getDescriptorForType().getName());
        final String s = XmlHelper.asXML(rootElement);
        xmlFormat.merge(s, emptyRegistry, resultBuilder);

        @SuppressWarnings("unchecked") final T data = (T)resultBuilder.build();
        return new XmlCallResult<>(chaincodeResultBuilder.build(), data);
    }

    public <T extends com.google.protobuf.Message>
    CompletableFuture<XmlCallResult<T>> sendRequest(Descriptors.MethodDescriptor methodDescriptor,
                          XmlCall.ChaincodeRequest chaincodeRequest,
                          com.google.protobuf.Message message,
                          Class<T> klass,
                          Function<String,String> processor) throws Exception {


        return CompletableFuture.supplyAsync(() -> {
            try {
                final String req = makeCallXML(methodDescriptor, chaincodeRequest, message);
                XmlHelper.xmlValidate(req, xsdFactory);
                return req;
            } catch (Throwable e) {
                throw new RuntimeException("XML build failed", e);
            }
        })
        .thenApplyAsync(processor)
        .thenApplyAsync(reply -> {
            try {
                XmlHelper.xmlValidate(reply, xsdFactory);
                return parseResponseXML(reply, klass);
            } catch (Exception e) {
                throw new RuntimeException("XML parsing failed", e);
            }
        });
    }
}
