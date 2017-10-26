import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.googlecode.protobuf.format.XmlJavaxFormat;
import com.googlecode.protobuf.format.bits.Base64Serializer;
import com.luxoft.uhg.fabric.proto.InsuredRegistry;
import com.luxoft.uhg.fabric.proto.TestObjects;
import org.junit.Test;

import javax.xml.stream.*;
import javax.xml.transform.dom.DOMResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class XmlFormatTest {

    @Test
    public void xmlRepresentaion()
    {
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

        final TestObjects.TestMessage message = builder.build();

        final XmlJavaxFormat xmlFormat = new XmlJavaxFormat( new Base64Serializer());

        String s = xmlFormat.printToString(message);

        System.out.println(s);
    }

    @Test
    public void test2()
    {
        final InsuredRegistry.MemberList.Builder memberListBuilder = InsuredRegistry.MemberList.newBuilder();

        String[] names = {"ID0", "ID1"};
        for (String name : names) {
            final InsuredRegistry.Member.Builder builder = InsuredRegistry.Member.newBuilder();

            builder.setId(name)
                    .putChannelIds(0, name + ":STR0")
                    .putChannelIds(1, name + ":STR1");
            memberListBuilder.addMembers(builder);
        }

        String s = new XmlJavaxFormat().printToString(memberListBuilder.build());
    }

    @Test
    public void emptyFields()
    {
        final TestObjects.TestMessage.Builder builder = TestObjects.TestMessage.newBuilder();

        // builder.addArr("wer").addArr("ewq");
        builder.putIntMap(0,1);
        builder.putIntMap(1,2);

        final XmlJavaxFormat xmlFormat = new XmlJavaxFormat();
        xmlFormat.setPrintEmptyScalars(true);
        xmlFormat.setPrintEmptyArray(true);
        String s = xmlFormat.printToString(builder.build());
        System.out.println(s);
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
                "</TestMessage>\n";

        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        final XMLEventReader xmlEventReader = inputFactory.createXMLEventReader(new StringReader(xmlText));
        final XmlJavaxFormat xmlFormat = new XmlJavaxFormat(new Base64Serializer());

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
}
