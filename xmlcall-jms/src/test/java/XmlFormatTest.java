import com.google.protobuf.ByteString;
import com.googlecode.protobuf.format.XmlFormat;
import com.luxoft.uhg.fabric.proto.InsuredRegistry;
import com.luxoft.uhg.fabric.proto.TestObjects;
import org.junit.Test;

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

        final XmlFormat xmlFormat = new XmlFormat();

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

        String s = new XmlFormat().printToString(memberListBuilder.build());
    }
}
