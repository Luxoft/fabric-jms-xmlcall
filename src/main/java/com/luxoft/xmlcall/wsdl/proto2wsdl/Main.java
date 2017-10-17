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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Main
{
    enum BuildType
    {
        XmlSchema, WSDL
    }

    private static final String targetNamespacePrefix = "http://www.luxoft.com/";

    private static Map<String,Descriptors.Descriptor>
    buildTypeMap(ProtoLoader pb, Map<String,String> args)
    {
        Map<String,Descriptors.Descriptor> result = new HashMap<>();

        for (Map.Entry<String, String> e : args.entrySet()) {
            result.put(e.getKey(), pb.getType(e.getValue()));
        }
        return result;
    }

    private static void builder(Function<WSDLBuilder, String> generator,
                                String inputFile,
                                String outputFile,
                                String serviceName,
                                String targetNamespace,
                                String faultTypeName,
                                Map<String,String> extraInput_,
                                Map<String,String> extraOutput_)
            throws Exception {

        final ProtoLoader protoLoader = new ProtoLoader(inputFile);
        final Descriptors.Descriptor faultType = protoLoader.getType(faultTypeName);
        final Map<String,Descriptors.Descriptor> extraInput = buildTypeMap(protoLoader, extraInput_);
        final Map<String,Descriptors.Descriptor> extraOutput = buildTypeMap(protoLoader, extraOutput_);

        final WSDLBuilder wsdlBuilder = new WSDLBuilder(protoLoader.getServices(), targetNamespace, serviceName, faultType, extraInput, extraOutput);

        final String s = generator.apply(wsdlBuilder);
        Files.write(Paths.get(outputFile), s.getBytes(StandardCharsets.UTF_8));
    }

    private static void updateArg(AtomicReference<String> arg, Supplier<String> evalDefault)
    {
        if (arg.get() == null)
            arg.set(evalDefault.get());
    }

    private static Consumer<String> updateMap(Map<String, String> map)
    {
        return s -> {
            int p = s.indexOf(":");
            if (p < 0)
                throw new RuntimeException("':' missed in 's'");
            map.put(s.substring(0, p), s.substring(p+1));
        };
    }

    public static void main(String[] args) throws Exception {
        BuildType buildType = null;
        final AtomicReference<String> inputFile = new AtomicReference<>(null);
        final AtomicReference<String> outputFile = new AtomicReference<>(null);
        final AtomicReference<String> serviceName = new AtomicReference<>(null);
        final AtomicReference<String> namespaceName = new AtomicReference<>(null);
        final AtomicReference<String> faultType = new AtomicReference<>(null);
        final AtomicReference<String> soapAddress = new AtomicReference<>(null);
        final AtomicReference<String> soapTransport = new AtomicReference<>(null);

        final Map<String, String> extraInput = new HashMap<>();
        final Map<String, String> extraOutput = new HashMap<>();

        boolean argsSection = true;
        Consumer<String> nextArg = null;

        for (String arg : args) {
            if (nextArg != null) {
                nextArg.accept(arg);
                nextArg = null;
                continue;
            }
            if (argsSection && arg.startsWith("-")) {
                switch (arg) {
                    case "-schema":
                        buildType = BuildType.XmlSchema;
                        break;
                    case "-wsdl":
                        buildType = BuildType.WSDL;
                        break;
                    case "-name":
                        nextArg = serviceName::set;
                        break;
                    case "-ns":
                        nextArg = namespaceName::set;
                        break;
                    case "-faultType":
                        nextArg = faultType::set;
                        break;
                    case "-extraInput":
                        nextArg = updateMap(extraInput);
                        break;
                    case "-extraOutput":
                        nextArg = updateMap(extraOutput);
                        break;
                    case "-soap-endpoint":
                        nextArg = soapAddress::set;
                        break;
                    case "-soap-transport-schema":
                        nextArg = soapTransport::set;
                        break;
                    case "--":
                        argsSection = false;
                        break;
                    default:
                        throw new RuntimeException("Unsupported argument " + arg);
                }
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
        updateArg(faultType, () -> "xmlcall.ChaincodeFault");
        if (extraInput.isEmpty())
            updateMap(extraInput).accept("chaincode:xmlcall.ChaincodeRequest");

        if (extraOutput.isEmpty())
            updateMap(extraOutput).accept("chaincode:xmlcall.ChaincodeResult");

        builder(generator,
                inputFile.get(),
                outputFile.get(),
                serviceName.get(),
                namespaceName.get(),
                faultType.get(),
                extraInput,
                extraOutput);
    }
}
