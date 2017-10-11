package com.luxoft.xmlcall.wsdl.proto2wsdl;

import com.luxoft.xmlcall.shared.ProtoLoader;
import com.luxoft.xmlcall.wsdl.WSDLBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main
{
    public static void buildWSDL(String inputFile, String outputFile) throws Exception {
        final ProtoLoader protoLoader = new ProtoLoader(inputFile);
        final WSDLBuilder wsdlBuilder = new WSDLBuilder();

        final String s = wsdlBuilder.buildWSDL(protoLoader.getServices());
        Files.write(Paths.get(outputFile), s.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        String inputFile = "data/proto/services.desc";
        String outputFile = "data/wsdl/services.wsdl";

        buildWSDL(inputFile, outputFile);
    }
}
