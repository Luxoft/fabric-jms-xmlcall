package com.luxoft.xmlcall.wsdl;

import com.google.protobuf.Descriptors;
import com.luxoft.xmlcall.proto.XmlCall;
import org.apache.commons.text.StrSubstitutor;

import java.util.*;

public class WSDLBuilder
{
    final String xsd = "xsd:";
    final String wsdl = "wsdl:";
    final String tns = "tns:";
    final String namespace = "http://www.luxoft.com/wsdl";

    private static final String xmlHeader =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";

    private static final String definitionsBegin =
            // ServiceName=HelloService
            // namespace=http://www.luxoft.com/wsdl
            "<${wsdl}definitions name=\"${serviceName}\"\n" +
                    " targetNamespace=\"${namespace}/${serviceName}.wsdl\"\n"+
                    " xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
                    " xmlns:tns=\"${namespace}/${serviceName}.wsdl\"\n"+
                    " xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"\n"+
                    " xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\"\n"+
                    ">\n";
    private static final String definitionsEnd =
            "</${wsdl}definitions>\n";

    private class Builder
    {
        HashMap<String, String> map = new HashMap<>();

        Builder()
        {
            map.put("xsd", xsd);
            map.put("wsdl", wsdl);
            map.put("tns", tns);
        }

        Builder add(String name, String value)
        {
            map.put(name, value);
            return this;
        }

        String build(String str)
        {
            StrSubstitutor sub = new StrSubstitutor(map);
            return sub.replace(str);
        }
    }

    private String mapType(Descriptors.FieldDescriptor fieldDescriptor)
    {
        final Descriptors.FieldDescriptor.Type type = fieldDescriptor.getType();
        switch (type) {
        case DOUBLE: return xsd + "double";
        case FLOAT:  return xsd + "float";
        case INT64:  return xsd + "long";
        case SINT64: return xsd + "long";
        case UINT64: return xsd + "unsignedLong";
        case INT32:  return xsd + "int";
        case SINT32: return xsd + "int";
        case UINT32: return xsd + "unsignedInt";
        case SFIXED64: return xsd + "long";
        case FIXED64: return xsd + "unsignedLong";
        case SFIXED32: return xsd + "int";
        case FIXED32: return xsd + "unsignedInt";
        case BOOL: return xsd + "boolean";
        case STRING: return xsd + "string";
        case GROUP:
        case MESSAGE:
            return fieldDescriptor.getMessageType().getName();

        case BYTES: return xsd + "base64Binary";
        case ENUM: // todo: map to enumeration
            throw new RuntimeException("Unhandled type: " + type.name());
        }

        throw new InternalError("Unhandled type: " + type.name());
    }

    private String buildXSDType(Descriptors.Descriptor type)
    {
        StringBuilder sb = new StringBuilder();
        Builder builder = new Builder()
                .add("typeName", type.getName());

        sb.append(builder.build("<${xsd}element name=\"${typeName}\">"));
        sb.append(builder.build("<${xsd}complexType>"));
        sb.append(builder.build("<${xsd}sequence>"));

        for (Descriptors.FieldDescriptor fieldDescriptor : type.getFields()) {
            builder.add("fieldName", fieldDescriptor.getName());
            builder.add("fieldType", mapType(fieldDescriptor));

            if (fieldDescriptor.isRepeated())
                sb.append(builder.build("<${xsd}element name=\"${fieldName}\" type=\"${fieldType}\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>"));
            else
                sb.append(builder.build("<${xsd}element name=\"${fieldName}\" type=\"${fieldType}\"/>"));
        }
        sb.append(builder.build("</${xsd}sequence>"));
        sb.append(builder.build("</${xsd}complexType>"));
        sb.append(builder.build("</${xsd}element>"));
        return sb.toString();
    }

    private String buildTypesSection(Set<Descriptors.Descriptor> types)
    {
        StringBuilder sb = new StringBuilder();
        Builder builder = new Builder();

        for (Descriptors.Descriptor descriptor : types) {
            sb.append(buildXSDType(descriptor));
            sb.append('\n');
        }

        final String s = sb.toString();
        if (!s.isEmpty())
            return builder.build("<${wsdl}types>" + s + "</${wsdl}types>");

        return "";
    }

    private String buildMessagesSection(Set<Descriptors.Descriptor> messages)
    {
        StringBuilder sb = new StringBuilder();
        Builder builder = new Builder();

        for (Descriptors.Descriptor descriptor : messages) {
            builder.add("messageName", descriptor.getName());
            sb.append(builder.build("<${wsdl}message name=\"${messageName}\">"));
            sb.append('\n');


            if (true) { // single part
                builder.add("paramType", descriptor.getName());
                builder.add("paramName", "param");

                sb.append(builder.build("<${wsdl}part name=\"${paramName}\" type=\"${tns}${paramType}\"/>"));
                sb.append('\n');
            }

            else { // multipart
                for (Descriptors.FieldDescriptor fieldDescriptor : descriptor.getFields()) {
                    builder.add("fieldName", fieldDescriptor.getName());
                    builder.add("fieldType", mapType(fieldDescriptor));
                    sb.append(builder.build("<${wsdl}part name=\"${fieldName}\" type=\"${fieldType}\"/>"));
                    sb.append('\n');
                }
            }
            sb.append(builder.build("</${wsdl}message>"));
            sb.append('\n');
        }

        return sb.toString();
    }


    private void markTypesRecursively(Descriptors.Descriptor descriptor,
                                      Set<Descriptors.Descriptor> set)
    {
        if (!set.add(descriptor))
            return;

        for (Descriptors.FieldDescriptor fieldDescriptor : descriptor.getFields()) {
            switch (fieldDescriptor.getType()) {
                case DOUBLE:
                case FLOAT:
                case INT64:
                case UINT64:
                case INT32:
                case FIXED64:
                case FIXED32:
                case BOOL:
                case STRING:
                case BYTES:
                case UINT32:
                case SFIXED32:
                case SFIXED64:
                case SINT32:
                case SINT64:
                    break;
                case ENUM:
                case GROUP:
                case MESSAGE:
                    final Descriptors.Descriptor messageType = fieldDescriptor.getMessageType();
                    markTypesRecursively(messageType, set);
                    break;
            }
        }
    }

    private static boolean isEmptyType(Descriptors.Descriptor type)
    {
        return type.getFields().isEmpty();
    }

    private String buildPortsSection(Descriptors.ServiceDescriptor serviceDescriptor)
    {
        StringBuilder sb = new StringBuilder();
        Builder builder = new Builder()
                .add("serviceName", serviceDescriptor.getName());

        serviceDescriptor.getMethods();

        sb.append(builder.build("<${wsdl}portType name=\"${serviceName}\">"));
        sb.append('\n');
        for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {

            final XmlCall.ExecType execType = methodDescriptor.getOptions().getExtension(XmlCall.execType);
            builder.add("methodName", methodDescriptor.getName());
            sb.append(builder.build("<${wsdl}operation name=\"${methodName}\">\n"));

            switch (execType) {

                case UNKNOWN:
                case UNRECOGNIZED:
                    throw new RuntimeException("Unhandled execType: " + execType.ordinal());

                case QUERY:
                case INVOKE:
                    builder.add("inputType", methodDescriptor.getInputType().getName());
                    builder.add("outputType", methodDescriptor.getOutputType().getName());

                    sb.append(builder.build("<${wsdl}input message=\"${tns}${inputType}\"/> \n"));
                    sb.append(builder.build("<${wsdl}output message=\"${tns}${outputType}\"/> \n"));
                    break;
                case EVENT:
                    builder.add("outputType", methodDescriptor.getOutputType().getName());
                    sb.append(builder.build("<${wsdl}output message=\"${tns}${outputType}\"/> \n"));
                    break;
            }
            sb.append(builder.build("</${wsdl}operation>"));
            sb.append('\n');
        }
        sb.append(builder.build("</${wsdl}portType>"));
        sb.append('\n');

        return sb.toString();
    }

    public String buildWSDL(List<Descriptors.ServiceDescriptor> serviceDescriptors)
    {
        Set<Descriptors.Descriptor> messageTypes = new HashSet<>();
        Set<Descriptors.Descriptor> regularTypes = new HashSet<>();

        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                final Descriptors.Descriptor outputType = methodDescriptor.getOutputType();
                final Descriptors.Descriptor inputType = methodDescriptor.getInputType();
                markTypesRecursively(inputType, regularTypes);
                markTypesRecursively(outputType, regularTypes);

                messageTypes.add(inputType);
                messageTypes.add(outputType);
            }
        }

        /**/

        Builder builder = new Builder();
        final StringBuilder doc = new StringBuilder();

        doc.append(builder.build(xmlHeader));
        doc.append(builder.build(definitionsBegin));

        doc.append(buildTypesSection(regularTypes));
        doc.append(buildMessagesSection(messageTypes));

        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            doc.append(buildPortsSection(serviceDescriptor));
        }

        doc.append(builder.build(definitionsEnd));

        return doc.toString();
    }
}
