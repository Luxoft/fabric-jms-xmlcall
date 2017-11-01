package com.luxoft.xmlcall.wsdl;

import com.google.protobuf.Descriptors;
import com.luxoft.xmlcall.proto.XmlCall;
import com.luxoft.xmlcall.shared.ProtoLoader;
import com.luxoft.xmlcall.shared.XmlHelper;
import org.apache.commons.text.StrSubstitutor;

import java.util.*;
import java.util.function.BiConsumer;

/*todo:
 * - extensions? in proto3 extendions are deprecated
 * - service input/output streams.
 */

public class WSDLBuilder
{
    private static final String xmlHeader =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";

    private static final String definitionsBegin =
            // ServiceName=HelloService
            // namespace=http://www.luxoft.com/wsdl
            "<${wsdl}definitions name=\"${serviceName}\"\n" +
                    " targetNamespace=\"${namespace}\"\n"+
                    " xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
                    " xmlns:tns=\"${namespace}\"\n"+
                    " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n"+
                    " xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\"\n"+
                    " xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"" +
                    ">\n";
    private static final String definitionsEnd =
            "</${wsdl}definitions>\n";

    public static final String soapHttpTransport = "http://schemas.xmlsoap.org/soap/http";
    public static final String soapJmsTransport = "http://www.w3.org/2010/soapjms/";

    enum ExtraAttribType
    {
        ATTRIBUTES, MESSAGE
    }
    private final String xs = "xs:";
    private final String wsdl = "wsdl:";
    private final String tns = "tns:";
    private final String namespace;
    private final String serviceName;
    private final ExtraAttribType extraAttribType = ExtraAttribType.ATTRIBUTES;
    private final Descriptors.Descriptor faultType;
    private final Descriptors.Descriptor inputAttributes;
    private final Descriptors.Descriptor outputAttributes;

    private final Set<Descriptors.ServiceDescriptor> serviceDescriptors;
    private final Set<Descriptors.Descriptor> messageTypes = new HashSet<>();
    private final Set<Descriptors.Descriptor> inputTypes = new HashSet<>();
    private final Set<Descriptors.Descriptor> outputTypes = new HashSet<>();
    private final Set<Descriptors.Descriptor> regularTypes = new HashSet<>();
    private final Set<Descriptors.EnumDescriptor> enumTypes = new HashSet<>();

    private class Builder
    {
        private StringBuilder sb = new StringBuilder();
        private HashMap<String, String> map = new HashMap<>();
        private StrSubstitutor sub = new StrSubstitutor(map);

        Builder()
        {
            map.put("xs", xs);
            map.put("wsdl", wsdl);
            map.put("tns", tns);
            map.put("soap", "soap:");
            map.put("namespace", namespace);
            map.put("serviceName", serviceName);
            map.put("soapNamespace", namespace);
        }

        Builder set(String name, String value)
        {
            map.put(name, value);
            return this;
        }

        public Builder appendLiteral(String s) {
            sb.append(s);
            return this;
        }

        Builder append(String str)
        {
            sb.append(sub.replace(str));
            return this;
        }

        @Override
        public String toString() {
            return sb.toString();
        }

    }

    public WSDLBuilder(ProtoLoader protoLoader, String serviceName)
    {
        this.serviceName = serviceName;
        this.serviceDescriptors = protoLoader.getServices();
        this.faultType = protoLoader.getFaultType();
        this.inputAttributes = protoLoader.getRequestAttributes();
        this.outputAttributes = protoLoader.getResultAttributes();
        this.namespace = protoLoader.getNamespaceURI();

        markTypesRecursively(faultType, regularTypes, enumTypes);
        messageTypes.add(faultType);
        if (extraAttribType == ExtraAttribType.MESSAGE) {
            markTypesRecursively(inputAttributes, regularTypes, enumTypes);
            // messageTypes.add(inputAttributes);
            markTypesRecursively(outputAttributes, regularTypes, enumTypes);
            // messageTypes.add(outputAttributes);
        }

        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                final Descriptors.Descriptor outputType = methodDescriptor.getOutputType();
                final Descriptors.Descriptor inputType = methodDescriptor.getInputType();
                markTypesRecursively(inputType, regularTypes, enumTypes);
                markTypesRecursively(outputType, regularTypes, enumTypes);

                messageTypes.add(inputType);
                messageTypes.add(outputType);

                inputTypes.add(inputType);
                outputTypes.add(outputType);
            }
        }
        if (faultType != null)
            outputTypes.add(faultType);
    }

    private String getEntryName(Descriptors.FieldDescriptor fieldDescriptor)
    {
        final Descriptors.FieldDescriptor.Type type = fieldDescriptor.getType();
        switch (type) {
            case DOUBLE:
            case FLOAT:
            case INT64:
            case SINT64:
            case UINT64:
            case INT32:
            case SINT32:
            case UINT32:
            case SFIXED64:
            case FIXED64:
            case SFIXED32:
            case FIXED32:
            case BOOL:
            case STRING:
            case BYTES:
                return "entry";
            case GROUP:
            case MESSAGE:
                return fieldDescriptor.getMessageType().getFullName();

            case ENUM: // todo: map to enumeration
                throw new RuntimeException("Unhandled type: " + type.name());
        }

        throw new InternalError("Unhandled type: " + type.name());
    }

    private String getXSDType(Descriptors.FieldDescriptor fieldDescriptor)
    {
        final Descriptors.FieldDescriptor.Type type = fieldDescriptor.getType();
        switch (type) {
        case DOUBLE: return xs + "double";
        case FLOAT:  return xs + "float";
        case INT64:  return xs + "long";
        case SINT64: return xs + "long";
        case UINT64: return xs + "unsignedLong";
        case INT32:  return xs + "int";
        case SINT32: return xs + "int";
        case UINT32: return xs + "unsignedInt";
        case SFIXED64: return xs + "long";
        case FIXED64: return xs + "unsignedLong";
        case SFIXED32: return xs + "int";
        case FIXED32: return xs + "unsignedInt";
        case BOOL: return xs + "boolean";
        case STRING: return xs + "string";
        case BYTES: return xs + "base64Binary";
        case GROUP:
        case MESSAGE:
            return tns + fieldDescriptor.getMessageType().getFullName();

        case ENUM:
            return tns + fieldDescriptor.getEnumType().getFullName();
        }

        throw new InternalError("Unhandled type: " + type.name());
    }

    private String buildXSDEnumType(Descriptors.EnumDescriptor type)
    {
        Builder builder = new Builder()
                .set("typeName", type.getFullName());

        builder.append("<${xs}simpleType name=\"${typeName}\" final=\"restriction\">");
        builder.append("<${xs}restriction base=\"${xs}string\">");
        for (Descriptors.EnumValueDescriptor enumValueDescriptor : type.getValues()) {
            builder.set("enumValue", enumValueDescriptor.getName());
            builder.append("<${xs}enumeration value=\"${enumValue}\"/>");
        }
        builder.append("</${xs}restriction>");
        builder.append("</${xs}simpleType>");
        return builder.toString();
    }

    private String buildXSDFieldElement(Descriptors.FieldDescriptor fieldDescriptor)
    {
        Builder builder = new Builder();
        builder.set("fieldName", fieldDescriptor.getName());
        builder.set("fieldType", getXSDType(fieldDescriptor));

        if (fieldDescriptor.isRepeated()) {
//                        builder.set("entryName", getEntryName(fieldDescriptor));
//                        builder.append(
//                                "<xs:element name=\"${fieldName}\">" +
//                                        "<xs:complexType>" +
//                                        "<xs:sequence>" +
//                                        "<xs:element name=\"${entryName}\" type=\"${fieldType}\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>" +
//                                        "</xs:sequence>" +
//                                        "</xs:complexType>" +
//                                        "</xs:element>");

            builder.append("<${xs}element name=\"${fieldName}\" type=\"${fieldType}\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>");
        } else
            builder.append("<${xs}element name=\"${fieldName}\" type=\"${fieldType}\"/>");
        return builder.toString();
    }

    private String buildXSDMessageType(Descriptors.Descriptor type)
    {
        Builder builder = new Builder()
                .set("typeName", type.getFullName());

        final List<Descriptors.FieldDescriptor> fields = type.getFields();

        final boolean hasFields = !fields.isEmpty();
        final boolean isMessage = messageTypes.contains(type);
        final boolean isInputMessage = isMessage && inputTypes.contains(type);
        final boolean isOutputMessage = isMessage && outputTypes.contains(type);
        final boolean useAttributes = extraAttribType == ExtraAttribType.ATTRIBUTES;
        final boolean hasInputAttributes = useAttributes && !inputAttributes.getFields().isEmpty();
        final boolean hasOutputAttributes = useAttributes && !outputAttributes.getFields().isEmpty();

        final boolean hasContent =
                hasFields
                || (isInputMessage && hasInputAttributes)
                || (isOutputMessage && hasOutputAttributes);

        if (!hasContent) {
            builder.append("<${xs}complexType name=\"${typeName}\"/>");
        }
        else {
            builder.append("<${xs}complexType name=\"${typeName}\">");
            if (hasFields) {
                Set<Descriptors.FieldDescriptor> oneofFields = new HashSet<>();
                for (Descriptors.OneofDescriptor oneofDescriptor : type.getOneofs()) {
                    for (Descriptors.FieldDescriptor fieldDescriptor : oneofDescriptor.getFields()) {
                        oneofFields.add(fieldDescriptor);
                    }
                }

                builder.append("<${xs}sequence>");

                for (Descriptors.FieldDescriptor fieldDescriptor : fields) {
                    if (oneofFields.contains(fieldDescriptor))
                        continue;
                    builder.appendLiteral(buildXSDFieldElement(fieldDescriptor));
                }

                for (Descriptors.OneofDescriptor oneofDescriptor : type.getOneofs()) {
                    builder.append("<${xs}choice>");
                    for (Descriptors.FieldDescriptor fieldDescriptor : oneofDescriptor.getFields()) {
                        builder.appendLiteral(buildXSDFieldElement(fieldDescriptor));
                    }
                    builder.append("</${xs}choice>");
                }

                builder.append("</${xs}sequence>");
            }

            if (extraAttribType == ExtraAttribType.ATTRIBUTES) {
                if (isInputMessage) {
                    for (Descriptors.FieldDescriptor fieldDescriptor : inputAttributes.getFields()) {
                        builder.set("attrName", fieldDescriptor.getName());
                        builder.set("attrType", getXSDType(fieldDescriptor));
                        builder.set("attrDir", XmlHelper.getDirPrefix(XmlHelper.Dir.IN));
                        builder.append("<xs:attribute name=\"${attrDir}${attrName}\" type=\"${attrType}\"/>");
                    }
                }

                if (isOutputMessage) {
                    for (Descriptors.FieldDescriptor fieldDescriptor : outputAttributes.getFields()) {
                        builder.set("attrName", fieldDescriptor.getName());
                        builder.set("attrType", getXSDType(fieldDescriptor));
                        builder.set("attrDir", XmlHelper.getDirPrefix(XmlHelper.Dir.OUT));
                        builder.append("<xs:attribute name=\"${attrDir}${attrName}\" type=\"${attrType}\"/>");
                    }
                }
            }
            builder.append("</${xs}complexType>");
        }

        return builder.toString();
    }

    private String buildXSDMessageElement(Descriptors.Descriptor type,
                                          Descriptors.Descriptor extraFields,
                                          String elementName)
    {
        Builder builder = new Builder();
        builder.set("elementName", elementName);
        builder.set("typeName", type.getFullName());

        if (extraAttribType == ExtraAttribType.ATTRIBUTES || extraFields.getFields().isEmpty())
            builder.append("<${xs}element name=\"${elementName}\" type=\"${tns}${typeName}\"/>\n");
        else {
            builder.set("paramName", type.getName());
            builder.append("<${xs}element name=\"${elementName}\">\n");
            builder.append("<${xs}complexType>\n");
            builder.append("<${xs}sequence>\n");
            builder.append("<${xs}element name=\"${paramName}\" type=\"${tns}${typeName}\"/>\n");

            if (extraAttribType == ExtraAttribType.MESSAGE) {
                builder.set("paramName", extraFields.getName());
                builder.set("paramType", extraFields.getFullName());
                builder.append("<${xs}element name=\"${paramName}\" type=\"${tns}${paramType}\"/>\n");
            }
            builder.append("</${xs}sequence>\n");
            builder.append("</${xs}complexType>\n");
            builder.append("</${xs}element>\n");
        }
        return builder.toString();
    }

    private String buildXSDMessageElements()
    {
        Builder builder = new Builder();
        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods())
                builder.appendLiteral(buildXSDMessageElement(methodDescriptor.getInputType(), inputAttributes, methodDescriptor.getFullName()));
        }

        for (Descriptors.Descriptor e : outputTypes) {
            builder.appendLiteral(buildXSDMessageElement(e, outputAttributes, e.getFullName()));
        }
        return builder.toString();
    }

    private String buildXSDMessages(Set<Descriptors.Descriptor> types)
    {
        Builder builder = new Builder();

        for (Descriptors.Descriptor descriptor : types) {
            builder.appendLiteral(buildXSDMessageType(descriptor));
            builder.appendLiteral("\n");
        }

        return builder.toString();
    }

    private String buildXSDEnums(Set<Descriptors.EnumDescriptor> types)
    {
        Builder builder = new Builder();

        for (Descriptors.EnumDescriptor descriptor : types) {
            builder.appendLiteral(buildXSDEnumType(descriptor));
            builder.appendLiteral("\n");
        }

        return builder.toString();
    }

    private String buildTypesSection()
    {
        Builder builder = new Builder();
        builder.append("<${wsdl}types>\n");
        builder.appendLiteral(buildInlineXmlSchema());
        builder.append("</${wsdl}types>\n");
        return builder.toString();
    }

    private String buildMessagesSection()
    {
        Builder builder = new Builder();

        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                // input message
                builder.set("messageName", methodDescriptor.getFullName());
                builder.set("paramType", methodDescriptor.getFullName());
                builder.set("paramName", methodDescriptor.getInputType().getName());

                builder.append("<${wsdl}message name=\"${messageName}\">");
                builder.append("<${wsdl}part name=\"${paramName}\" element=\"${tns}${paramType}\"/>");
                builder.append("</${wsdl}message>\n");
            }
        }

        for (Descriptors.Descriptor e : outputTypes) {
            builder.set("messageName", e.getFullName());
            builder.set("paramType", e.getFullName());
            builder.set("paramName", e.getName());

            builder.append("<${wsdl}message name=\"${messageName}\">");
            builder.append("<${wsdl}part name=\"${paramName}\" element=\"${tns}${paramType}\"/>");
            builder.append("</${wsdl}message>\n");
        }

        return builder.toString();
    }


    private static void markTypesRecursively(Descriptors.Descriptor descriptor,
                                      Set<Descriptors.Descriptor> messageSet,
                                      Set<Descriptors.EnumDescriptor> enumTypeSet)
    {
        if (!messageSet.add(descriptor))
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
                    final Descriptors.EnumDescriptor enumType = fieldDescriptor.getEnumType();
                    enumTypeSet.add(enumType);
                    break;

                case GROUP:
                case MESSAGE:
                    final Descriptors.Descriptor messageType = fieldDescriptor.getMessageType();
                    markTypesRecursively(messageType, messageSet, enumTypeSet);
                    break;
            }
        }
        for (Descriptors.Descriptor nf : descriptor.getNestedTypes()) {
            markTypesRecursively(nf, messageSet, enumTypeSet);
        }
    }

    private String buildPortsSection(Descriptors.ServiceDescriptor serviceDescriptor)
    {
        Builder builder = new Builder()
                .set("serviceName", serviceDescriptor.getName());

        builder.append("<${wsdl}portType name=\"${serviceName}\">\n");
        for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {

            final XmlCall.ExecType execType = methodDescriptor.getOptions().getExtension(XmlCall.execType);
            builder.set("methodName", methodDescriptor.getFullName());
            builder.append("<${wsdl}operation name=\"${methodName}\">");

            switch (execType) {

                case UNKNOWN:
                    throw new RuntimeException("execType is not set. You should use INVOKE or QUERY");

                case UNRECOGNIZED:
                    throw new RuntimeException("Unhandled execType: " + execType.ordinal());

                case QUERY:
                case INVOKE:
                    builder.set("inputType", methodDescriptor.getFullName());
                    builder.set("outputType", methodDescriptor.getOutputType().getFullName());
                    builder.set("faultType", faultType.getFullName());

                    builder.append("<${wsdl}input message=\"${tns}${inputType}\"/>");
                    builder.append("<${wsdl}output message=\"${tns}${outputType}\"/>");
                    builder.append("<${wsdl}fault name=\"fault\" message=\"${tns}${faultType}\"/>");
                    break;
                case EVENT:
                    builder.set("outputType", methodDescriptor.getFullName());
                    builder.append("<${wsdl}output message=\"${tns}${outputType}\"/>");
                    break;
            }
            builder.append("</${wsdl}operation>\n");
        }
        builder.append("</${wsdl}portType>\n");
        return builder.toString();
    }

    private String buildSoap(String soapAddress, String soapTransportSchema)
    {
        // for JMSL
        //   - soapAddress: jms:topic:test.cxf.jmstransport.topic
        //   - soapTransportSchema: http://www.w3.org/2010/soapjms/
        // for HTTP:
        //    - soapTransportSchema: http://schemas.xmlsoap.org/soap/http
        //
        Builder builder = new Builder();

        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            builder.set("bindingName", serviceDescriptor.getName() + "Binding");
            builder.set("portType", serviceDescriptor.getName());
            builder.set("soapTransport", soapTransportSchema);
            builder.set("soapAddress", soapAddress);

            builder.append("<${wsdl}binding name=\"${bindingName}\" type=\"${tns}${portType}\">\n");
            builder.append("<${soap}binding style=\"document\" transport=\"${soapTransport}\"/>\n");

            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                final XmlCall.ExecType execType = methodDescriptor.getOptions().getExtension(XmlCall.execType);

                builder.set("methodName", methodDescriptor.getFullName());
                builder.append("<${wsdl}operation name=\"${methodName}\">");
                builder.append("<${soap}operation soapAction=\"\"/>");

                switch (execType) {
                    case UNKNOWN:
                    case UNRECOGNIZED:
                        break;
                    case QUERY:
                    case INVOKE:
                        builder.append("<${wsdl}input>");
                        builder.append("<${soap}body use=\"literal\"/>");
                        builder.append("</${wsdl}input>");

                        builder.append("<${wsdl}output>");
                        builder.append("<${soap}body use=\"literal\"/>");
                        builder.append("</${wsdl}output>");

                        builder.append("<${wsdl}fault name=\"fault\">");
                        builder.append("<${soap}fault name=\"fault\" use=\"literal\"/>");
                        builder.append("</${wsdl}fault>");
                        break;
                    case EVENT:
                        builder.append("<${wsdl}output>");
                        builder.append("<${soap}body use=\"literal\"/>");
                        builder.append("</${wsdl}output>");
                        break;
                }
                builder.append("</${wsdl}operation>\n");
            }
            builder.append("</${wsdl}binding>\n");
        }

        /* services */
        builder.append("<${wsdl}service name=\"${serviceName}\">\n");
        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            builder.set("bindingName", serviceDescriptor.getFullName() + "Binding");
            builder.set("portName", serviceDescriptor.getName() + "Port");

            builder.append("<${wsdl}port binding=\"${tns}${bindingName}\" name=\"${portName}\">");
            builder.append("<soap:address location=\"${soapAddress}\"/>");
            builder.append("</${wsdl}port>\n");
        }
        builder.append("</${wsdl}service>\n");
        return builder.toString();
    }

    private String buildInlineXmlSchema()
    {
        Builder builder = new Builder();

        final String header = "<${xs}schema\n" +
                " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                " xmlns:tns=\"${namespace}\"\n" +
                " targetNamespace=\"${namespace}\"\n" +
                " elementFormDefault=\"qualified\">\n";

        final String footer = "</${xs}schema>\n";

        return builder.append(header)
                .appendLiteral(buildXSDEnums(this.enumTypes))
                .appendLiteral(buildXSDMessages(this.regularTypes))
                .appendLiteral(buildXSDMessageElements())
                .append(footer)
                .toString();
    }

    private void buildXmSchemaItem(Descriptors.Descriptor elementType,
                               Descriptors.Descriptor extraFields,
                               String elementName,
                               BiConsumer<String, String> continuation)
    {
        Set<Descriptors.Descriptor> descriptors = new HashSet<>();
        Set<Descriptors.EnumDescriptor> enumDescriptors = new HashSet<>();
        final String header = "<${xs}schema\n" +
                " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                " xmlns:tns=\"${namespace}\"\n" +
                " targetNamespace=\"${namespace}\"\n" +
                " elementFormDefault=\"qualified\">\n";

        final String footer = "</${xs}schema>\n";
        Builder builder = new Builder();
        // builder.set("namespace", namespace + "/" + elementType.getFullName());

        builder.append(header);

        descriptors.clear();
        markTypesRecursively(elementType, descriptors, enumDescriptors);

        if (extraAttribType == ExtraAttribType.MESSAGE) {
            markTypesRecursively(extraFields, descriptors, enumDescriptors);
        }
        builder.appendLiteral(buildXSDEnums(enumDescriptors));
        builder.appendLiteral(buildXSDMessages(descriptors));
        builder.appendLiteral(buildXSDMessageElement(elementType, extraFields, elementName));
        builder.append(footer);
        continuation.accept(elementName, builder.toString());
    }

    public void buildXmlSchema(BiConsumer<String, String> continuation)
    {
        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                buildXmSchemaItem(methodDescriptor.getInputType(), inputAttributes, methodDescriptor.getFullName(), continuation);
            }
        }

        for (Descriptors.Descriptor e : outputTypes) {
            buildXmSchemaItem(e, null, e.getFullName(), continuation);
        }
    }

    public String buildXmlSchema()
    {
        return buildInlineXmlSchema();
    }

    public String buildJaxb(String xsdFileName)
    {
        Builder builder = new Builder();

        builder.set("xsdFileName", xsdFileName);

        builder.append("<bindings version=\"2.0\" xmlns=\"http://java.sun.com/xml/ns/jaxb\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n");
        builder.append("  <bindings schemaLocation=\"${xsdFileName}\">\n");
        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                builder.set("methodName", methodDescriptor.getFullName());

                builder.append("    <bindings node=\"//xs:element[@name='${methodName}']\">\n");
                builder.append("      <class name=\"${methodName}\"/>\n");
                builder.append("    </bindings>\n");
            }
        }
        builder.append("  </bindings>\n");
        builder.append("</bindings>\n");

        return builder.toString();
    }

    public String buildWSDL(String soapAddress, String soapTransportSchema)
    {
        Builder builder = new Builder();

        builder.append(xmlHeader);
        builder.append(definitionsBegin);

        builder.appendLiteral(buildTypesSection());
        builder.appendLiteral(buildMessagesSection());

        for (Descriptors.ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            builder.appendLiteral(buildPortsSection(serviceDescriptor));
        }

        builder.appendLiteral(buildSoap(soapAddress, soapTransportSchema));
        builder.append(definitionsEnd);

        return builder.toString();
    }
}
