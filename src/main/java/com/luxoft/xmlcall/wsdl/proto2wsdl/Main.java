package com.luxoft.xmlcall.wsdl.proto2wsdl;

import com.google.protobuf.Descriptors;
import com.luxoft.xmlcall.shared.ProtoLoader;
import com.luxoft.xmlcall.util.Decode;
import com.luxoft.xmlcall.wsdl.WSDLBuilder;
import org.apache.commons.io.FilenameUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

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
                                String targetNamespace,
                                String faultTypeName,
                                String targetTypeName
                                ) throws Exception {

        final ProtoLoader protoLoader = new ProtoLoader(inputFile);
        final Descriptors.Descriptor faultType = protoLoader.getType(faultTypeName);
        final HashMap<String,Descriptors.Descriptor> extraInput = new HashMap<>();

        if (targetTypeName != null) {
            final String[] strings = targetTypeName.split("[:]");
            extraInput.put(strings[0], protoLoader.getType(strings[1]));
        }


        final WSDLBuilder wsdlBuilder = new WSDLBuilder(protoLoader.getServices(), targetNamespace, serviceName, faultType, extraInput, null);

        final String s = generator.apply(wsdlBuilder);
        Files.write(Paths.get(outputFile), s.getBytes(StandardCharsets.UTF_8));
    }

    private static void updateArg(AtomicReference<String> arg, Supplier<String> evalDefault)
    {
        if (arg.get() == null)
            arg.set(evalDefault.get());
    }

    public static void main(String[] args) throws Exception {
        BuildType buildType = null;
        final AtomicReference<String> inputFile = new AtomicReference<>(null);
        final AtomicReference<String> outputFile = new AtomicReference<>(null);
        final AtomicReference<String> serviceName = new AtomicReference<>(null);
        final AtomicReference<String> namespaceName = new AtomicReference<>(null);
        final AtomicReference<String> faultType = new AtomicReference<>(null);
        final AtomicReference<String> targetType = new AtomicReference<>(null);
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
                else if ("-faultType".equals(arg))
                    nextArg = faultType;
                else if ("-targetType".equals(arg))
                    nextArg = targetType;
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

        updateArg(inputFile, () -> "data/proto/services.desc");
        updateArg(outputFile, () -> {
            final String ext = new Decode<BuildType, String>(BT)
                    .when(BuildType.XmlSchema, ".xsd")
                    .when(BuildType.WSDL, ".wsdl")
                    .orThrow(() -> new InternalError("Unsupported case " + BT.name()));

            final String path = FilenameUtils.getPath(inputFile.get());
            final String baseName = FilenameUtils.getBaseName(inputFile.get());
            return Paths.get(path, baseName + ext).toString();
        });

        updateArg(soapAddress, () -> "http://localhost/${serviceName}/${portName}");
        updateArg(soapTransport, () -> WSDLBuilder.soapHttpTransport);

        final Function<WSDLBuilder, String> generator =
                new Decode<BuildType, Function<WSDLBuilder, String>>(buildType)
                    .when(BuildType.XmlSchema, WSDLBuilder::buildXmlSchema)
                    .when(BuildType.WSDL, wsdlBuilder -> wsdlBuilder.buildWSDL(soapAddress.get(), soapTransport.get()))
                    .orThrow(() -> new InternalError("Unsupported case " + BT.name()));

        updateArg(serviceName, () -> FilenameUtils.getBaseName(inputFile.get()));
        updateArg(namespaceName, () -> targetNamespacePrefix + FilenameUtils.getName(outputFile.get()));
        updateArg(faultType, () -> "xmlcall.Fault");
        updateArg(targetType, () -> "address:xmlcall.Address");

        builder(generator,
                inputFile.get(),
                outputFile.get(),
                serviceName.get(),
                namespaceName.get(),
                faultType.get(),
                targetType.get());
    }
}
