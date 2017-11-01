package com.luxoft.xmlcall.wsdl.proto2wsdl;

import com.luxoft.xmlcall.shared.ProtoLoader;
import com.luxoft.xmlcall.wsdl.WSDLBuilder;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Main {
    enum BuildType {
        XmlSchema, XmlSchemaSet, JAXB, WSDL
    }

    private static boolean isDirectory(String path) {
        if (path == null)
            return false;

        final Path p = Paths.get(path);
        return Files.exists(p) && Files.isDirectory(p);
    }


    private static void builder(BuildType buildType,
                                String inputFile,
                                String outputFile,
                                String serviceName,
                                String soapAddress,
                                String soapTransport,
                                String xsdFileName)
            throws Exception {

        final ProtoLoader protoLoader = new ProtoLoader(inputFile);

        final WSDLBuilder wsdlBuilder = new WSDLBuilder(protoLoader, serviceName);

        switch (buildType) {
            case XmlSchemaSet:
                wsdlBuilder.buildXmlSchema((typeName, s) -> {
                    try {
                        Files.write(Paths.get(outputFile, typeName + ".xsd"), s.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to write file", e);
                    }
                });
                break;

            case XmlSchema:
                final String xmlSchema = wsdlBuilder.buildXmlSchema();
                Files.write(Paths.get(outputFile), xmlSchema.getBytes(StandardCharsets.UTF_8));
                break;

            case JAXB:
                final String jaxb = wsdlBuilder.buildJaxb(xsdFileName);
                Files.write(Paths.get(outputFile), jaxb.getBytes(StandardCharsets.UTF_8));
                break;

            case WSDL:
                final String wsdl = wsdlBuilder.buildWSDL(soapAddress, soapTransport);
                Files.write(Paths.get(outputFile), wsdl.getBytes(StandardCharsets.UTF_8));
                break;
        }
    }

    private static void updateArg(AtomicReference<String> arg, Supplier<String> evalDefault) {
        if (arg.get() == null)
            arg.set(evalDefault.get());
    }

    public static void main(String[] args) throws Exception {
        BuildType buildType = null;
        final AtomicReference<String> inputFile = new AtomicReference<>(null);
        final AtomicReference<String> outputFile = new AtomicReference<>(null);
        final AtomicReference<String> serviceName = new AtomicReference<>(null);
        final AtomicReference<String> soapAddress = new AtomicReference<>(null);
        final AtomicReference<String> soapTransport = new AtomicReference<>(null);
        final AtomicReference<String> xsdFileName = new AtomicReference<>(null);

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
                    case "-xsd":
                    case "-schema":
                        buildType = BuildType.XmlSchema;
                        break;
                    case "-schema-set":
                        buildType = BuildType.XmlSchemaSet;
                        break;
                    case "-wsdl":
                        buildType = BuildType.WSDL;
                        break;
                    case "-xjb":
                    case "-jaxb":
                        buildType = BuildType.JAXB;
                        break;
                    case "-xsd-file":
                        nextArg = xsdFileName::set;
                        break;
                    case "-name":
                        nextArg = serviceName::set;
                        break;
                    case "-soap-endpoint":
                        nextArg = soapAddress::set;
                        break;
                    case "-soap-transport-schema":
                        nextArg = soapTransport::set;
                        break;
                    case "-output":
                        nextArg = outputFile::set;
                        break;
                    case "--":
                        argsSection = false;
                        break;
                    default:
                        throw new RuntimeException("Unsupported argument " + arg);
                }
            } else {
                if (inputFile.get() == null)
                    inputFile.set(arg);
                else
                    throw new RuntimeException("Unsupported positional argument " + arg);
            }
        }

        if (buildType == null)
            buildType = BuildType.WSDL;

        final BuildType BT = buildType;

//        updateArg(inputFile, () -> "data/proto/services.desc");
        updateArg(outputFile, () -> {
            switch (BT) {
                case XmlSchemaSet:
                    return FilenameUtils.getPath(inputFile.get());

                case XmlSchema: {
                    final String path = FilenameUtils.getPath(inputFile.get());
                    final String baseName = FilenameUtils.getBaseName(inputFile.get());
                    return Paths.get(path, baseName + ".xsd").toString();
                }

                case JAXB: {
                    final String path = FilenameUtils.getPath(inputFile.get());
                    final String baseName = FilenameUtils.getBaseName(inputFile.get());
                    return Paths.get(path, baseName + ".xjb").toString();
                }

                case WSDL: {
                    final String path = FilenameUtils.getPath(inputFile.get());
                    final String baseName = FilenameUtils.getBaseName(inputFile.get());
                    return Paths.get(path, baseName + ".wsdl").toString();
                }
                default:
                    throw new InternalError("Unsupported case " + BT.name());
            }
        });

        if (BT == BuildType.WSDL && isDirectory(outputFile.get())) {
            final String baseName = FilenameUtils.getBaseName(inputFile.get());
            outputFile.set(Paths.get(outputFile.get(), baseName + ".wsdl").toString());
        }

        updateArg(soapAddress, () -> "http://localhost/${serviceName}/${portName}");
        updateArg(soapTransport, () -> WSDLBuilder.soapHttpTransport);

        updateArg(serviceName, () -> FilenameUtils.getBaseName(inputFile.get()));

        updateArg(xsdFileName, () -> {
            final String path = FilenameUtils.getPath(inputFile.get());
            final String baseName = FilenameUtils.getBaseName(inputFile.get());
            return Paths.get(/*path, */baseName + ".xsd").toString();
        });

        if (inputFile.get() == null) {
            throw new IllegalArgumentException("input file is not specified");
        }

        builder(buildType,
                inputFile.get(),
                outputFile.get(),
                serviceName.get(),
                soapAddress.get(),
                soapTransport.get(),
                xsdFileName.get());
    }
}
