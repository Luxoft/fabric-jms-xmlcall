package com.luxoft.xmlcall.shared;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class Strings
{
    public static final String RequestSuffix = ".Request";
    public static final String ResponseSuffix = ".Response";

    public enum Dir
    {
        IN, OUT
    }

    private static String getPrefix(Dir dir)
    {
        switch (dir) {
            case IN:
                return "in.";
            case OUT:
                return "out.";
            default:
                throw new InternalError("Unhandled case " + dir.name());
        }
    }

    public static void loadAttributes(Message.Builder builder, Element node, Dir dir)
    {
        final String prefix = getPrefix(dir);
        for (Descriptors.FieldDescriptor fieldDescriptor : builder.getDescriptorForType().getFields()) {
            final String value = node.attributeValue(prefix + fieldDescriptor.getName());

            // todo: this works for STRING only
            builder.setField(fieldDescriptor, value);
        }
    }

    public static void pasteAttributes(Element node, Message message, Dir dir)
    {
        final String prefix = getPrefix(dir);
        for (Descriptors.FieldDescriptor fieldDescriptor : message.getDescriptorForType().getFields()) {
            node.addAttribute(prefix + fieldDescriptor.getName(), message.getField(fieldDescriptor).toString());
        }
    }

    public static String asXML(Element element)
    {
        OutputFormat format = new OutputFormat();
        format.setEncoding(StandardCharsets.UTF_8.displayName());

        format.setExpandEmptyElements(true);
        try {
            StringWriter out = new StringWriter();
            XMLWriter writer = new XMLWriter(out, format);

            writer.write(element);
            writer.flush();

            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException("IOException while generating textual "
                    + "representation: " + e.getMessage());
        }

    }

    public static void cleanAttributes(Element elem) {
        for (Iterator<Attribute> iterator = elem.attributes().iterator(); iterator.hasNext(); ) {
            iterator.next();
            iterator.remove();
        }
    }


}
