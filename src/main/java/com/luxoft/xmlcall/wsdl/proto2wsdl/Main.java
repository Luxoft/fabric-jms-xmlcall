package com.luxoft.xmlcall.wsdl.proto2wsdl;

import com.luxoft.xmlcall.shared.ProtoLoader;
import com.luxoft.xmlcall.util.Decode;
import com.luxoft.xmlcall.wsdl.WSDLBuilder;
import org.apache.commons.io.FilenameUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class Main
{
    enum BuildType
    {
        XmlSchema, WSDL
    }

    private static final String targetNamespacePrefix = "http://www.luxoft.com/";
    private static void builder(Function<WSDLBuilder, String> generator,
                                String inputFile,
                                String outputFile,
                                String serviceName,
                                String targetNamespace) throws Exception {

        final ProtoLoader protoLoader = new ProtoLoader(inputFile);
        final WSDLBuilder wsdlBuilder = new WSDLBuilder(protoLoader.getServices(), targetNamespace, serviceName);

        final String s = generator.apply(wsdlBuilder);
        Files.write(Paths.get(outputFile), s.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        BuildType buildType = null;
        final AtomicReference<String> inputFile = new AtomicReference<>(null);
        final AtomicReference<String> outputFile = new AtomicReference<>(null);
        final AtomicReference<String> serviceName = new AtomicReference<>(null);
        final AtomicReference<String> namespaceName = new AtomicReference<>(null);
        final AtomicReference<String> soapAddress = new AtomicReference<>(null);
        final AtomicReference<String> soapTransport = new AtomicReference<>(null);

        boolean argsSection = true;
        AtomicReference<String> nextArg = null;
        for (String arg : args) {
            if (nextArg != null) {
                nextArg.set(arg);
                nextArg = null;
                continue;
            }
            if (argsSection && arg.startsWith("-")) {
                if ("-schema".equals(arg))
                    buildType = BuildType.XmlSchema;
                else if ("-wsdl".equals(arg))
                    buildType = BuildType.WSDL;
                else if ("-name".equals(arg))
                    nextArg = serviceName;
                else if ("-ns".equals(arg))
                    nextArg = namespaceName;
                else if ("-soap-endpoint".equals(arg))
                    nextArg = soapAddress;
                else if ("-soap-transport-schema".equals(arg))
                    nextArg = soapTransport;
                else if ("--".equals(arg))
                    argsSection = false;
                else
                    throw new RuntimeException("Unsupported argument " + arg);
            }
            else {
                if (inputFile.get() == null)
                    inputFile.set(arg);
                else if (outputFile.get() == null)
                    outputFile.set(arg);
                else
                    throw new RuntimeException("Unsupported positional argument " + arg);
            }
        }

        if (buildType == null)
            buildType = BuildType.WSDL;

        final BuildType BT = buildType;

        if (inputFile.get() == null)
            inputFile.set("data/proto/services.desc");
        if (outputFile.get() == null) {
            final String ext = new Decode<BuildType, String>(buildType)
                    .when(BuildType.XmlSchema, ".xsd")
                    .when(BuildType.WSDL, ".wsdl")
                    .orThrow(() -> new InternalError("Unsupported case " + BT.name()));

            final String path = FilenameUtils.getPath(inputFile.get());
            final String baseName = FilenameUtils.getBaseName(inputFile.get());
            outputFile.set(Paths.get(path, baseName + ext).toString());
        }

        if (soapAddress.get() == null)
            soapAddress.set("http://localhost");

        if (soapTransport.get() == null)
            soapTransport.set(WSDLBuilder.soapHttpTransport);

        final Function<WSDLBuilder, String> generator =
                new Decode<BuildType, Function<WSDLBuilder, String>>(buildType)
                    .when(BuildType.XmlSchema, WSDLBuilder::buildXmlSchema)
                    .when(BuildType.WSDL, wsdlBuilder -> wsdlBuilder.buildWSDL(soapAddress.get(), soapTransport.get()))
                    .orThrow(() -> new InternalError("Unsupported case " + BT.name()));

        if (serviceName.get() == null)
            serviceName.set(FilenameUtils.getBaseName(inputFile.get()));

        if (namespaceName.get() == null)
            namespaceName.set(targetNamespacePrefix + FilenameUtils.getName(outputFile.get()));
        builder(generator, inputFile.get(), outputFile.get(), serviceName.get(), namespaceName.get());
    }
}
