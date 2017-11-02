import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.googlecode.protobuf.format.XmlJavaxFormat;
import com.googlecode.protobuf.format.bits.Base64Serializer;
import com.luxoft.uhg.fabric.proto.TestObjects;
import org.custommonkey.xmlunit.XMLAssert;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.stream.*;
import javax.xml.transform.dom.DOMResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class XmlFormatTest {

    XmlJavaxFormat xmlFormat;

    @Before
    public void setup()
    {
        xmlFormat = new XmlJavaxFormat(new Base64Serializer());
    }

    private void xmlStringProcessor(String input, String expected) throws IOException, XMLStreamException, SAXException {
        final TestObjects.TestMessage.Builder builder = TestObjects.TestMessage.newBuilder();
        final TestObjects.TestMessage.Builder result = TestObjects.TestMessage.newBuilder();
        builder.setS(input);

        xmlMessageProcessor(builder.build(), expected);
    }

    private void xmlMessageProcessor(Message input, String expected) throws IOException, XMLStreamException, SAXException {
        final Message.Builder resultBuilder = input.newBuilderForType();
        final String s = xmlFormat.printToString(input);
        if (expected != null)
            XMLAssert.assertXMLEqual(expected, s);

        xmlFormat.merge(s, ExtensionRegistry.getEmptyRegistry(), resultBuilder);
        assertEquals(input, resultBuilder.build());
    }

    @Test
    public void xmlRepresentaion() throws XMLStreamException, IOException, SAXException {
        final TestObjects.TestMessage.Builder builder = TestObjects.TestMessage.newBuilder();

        builder.putIntMap(0, 100);
        builder.putIntMap(1, 101);
        builder.putIntMap(2, 102);

        builder.putMsgMap("aaa",
                TestObjects.MapValue.newBuilder().setMapValueValue("AAA").build());
        builder.putMsgMap("bbb",
                TestObjects.MapValue.newBuilder().setMapValueValue("BBB").build());
        builder.putMsgMap("ccc",
                TestObjects.MapValue.newBuilder().setMapValueValue("CCC").build());

        builder.addArr("str1").addArr("str2").addArr("str3");

        builder.setB(ByteString.copyFrom("\0BINARY\1STRING\2".getBytes(StandardCharsets.UTF_8)));
        builder.setS("STRING\u0000STRING\1STRING\000\n\177");
        builder.setX(101);
        builder.setE(TestObjects.MYENUM.OPTB);
        builder.setSubMessage(TestObjects.MapValue.newBuilder().setMapValueValue("SSSS").build());
        builder.setNested(TestObjects.TestMessage.NestedMessage.newBuilder().setA(202).build());
        builder.setNestedEnum(TestObjects.TestMessage.NestedEnum.OPT1);

        final String expectedXml =
                "<TestMessage>" +
                        "<intMap><key>0</key><value>100</value></intMap>" +
                        "<intMap><key>1</key><value>101</value></intMap>" +
                        "<intMap><key>2</key><value>102</value></intMap>" +
                        "<msgMap><key>aaa</key><value><mapValueValue>AAA</mapValueValue></value></msgMap>" +
                        "<msgMap><key>bbb</key><value><mapValueValue>BBB</mapValueValue></value></msgMap>" +
                        "<msgMap><key>ccc</key><value><mapValueValue>CCC</mapValueValue></value></msgMap>" +
                        "<arr>str1</arr>" +
                        "<arr>str2</arr>" +
                        "<arr>str3</arr>" +
                        "<b>AEJJTkFSWQFTVFJJTkcC</b>" +
                        "<s>STRING\\u0000STRING\\u0001STRING\\u0000\n\\u007F</s>" +
                        "<x>101</x>" +
                        "<e>OPTB</e>" +
                        "<sub_message><mapValueValue>SSSS</mapValueValue></sub_message>" +
                        "<nested><a>202</a></nested>" +
                        "<nestedEnum>OPT1</nestedEnum>" +
                        "</TestMessage>";
        xmlMessageProcessor(builder.build(), expectedXml);
    }

    @Test
    public void emptyFields() throws XMLStreamException, IOException, SAXException {
        final TestObjects.TestMessage.Builder builder = TestObjects.TestMessage.newBuilder();

        xmlFormat.setPrintEmptyScalars(true);
        final String expectedXml = "<TestMessage>" +
                "<b></b>" +
                "<s></s>" +
                "<x>0</x>" +
                "<e>OPT0</e>" +
                "<nestedEnum>OPT0</nestedEnum>" +
                "</TestMessage>";
        xmlMessageProcessor(builder.build(), expectedXml);
    }

    @Test
    public void xmlParse() throws IOException, XMLStreamException {
        final TestObjects.TestMessage.Builder builder = TestObjects.TestMessage.newBuilder();
        final String xmlText =
                // "<?xml version=\"1.0\" encoding=\"utf-8\">\n" +
                "<TestMessage attr=\"qwe\">\n" +
//                "<b/>" +
//                "<b>QmFzZTY0IGZvcm1hdA==</b>" +
//                "<s>string 1 line</s>" +
//                "<x>1</x>" +
                "<e>OPTA</e>" +
                "</TestMessage>\n";

        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        final XMLEventReader xmlEventReader = inputFactory.createXMLEventReader(new StringReader(xmlText));

        xmlFormat.setPrintEmptyScalars(true);
        // xmlFormat.setPrintSelfClosedTags(true);
        xmlFormat.merge(xmlEventReader, ExtensionRegistry.getEmptyRegistry(), builder);
        final TestObjects.TestMessage message = builder.build();
        System.out.println(message.toString());

        final TestObjects.TestMessage.Builder resultBuilder = TestObjects.TestMessage.newBuilder();

        XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
        final StringWriter stringWriter = new StringWriter();
        final DOMResult domResult = new DOMResult();
        // final XMLStreamWriter xmlStreamWriter1 = outputFactory.createXMLStreamWriter(domResult);
        final XMLStreamWriter xmlStreamWriter = outputFactory.createXMLStreamWriter(stringWriter);
        xmlFormat.print(message, xmlStreamWriter);
        System.out.println(stringWriter.toString());

//        final XmlFormat xmlFormat = new XmlFormat();
//        xmlFormat.merge(xmlText, ExtensionRegistry.getEmptyRegistry(), builder);
//        final TestObjects.TestMessage message = builder.build();
//        System.out.println(message.toString());
    }

    @Test
    public void xmlCharacters() throws XMLStreamException, IOException, SAXException {
        xmlStringProcessor("<&", "<TestMessage><s>&lt;&amp;</s></TestMessage>");
        xmlStringProcessor("\u0000\uFFFF", "<TestMessage><s>\\u0000\\uFFFF</s></TestMessage>");
    }
}
