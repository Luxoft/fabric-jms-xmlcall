package com.luxoft.xmlcall.handler;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Main
{
    private void run(String fileName, String xmlRequest) throws Exception
    {
        XmlCallHandler xmlCallHandler = new XmlCallHandler(fileName);
        final byte[] bytes = Files.readAllBytes(Paths.get(xmlRequest));
        xmlCallHandler.processXmlMessage(new String(bytes), null);
    }

    public static void main(String[] args) throws Exception {
        new Main().run("data/proto/services.desc", "data/xml/claim.xml");
    }

}
