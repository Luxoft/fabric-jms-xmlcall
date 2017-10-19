package com.luxoft.xmlcall.shared;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.sun.msv.verifier.jarv.TheFactoryImpl;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXWriter;
import org.dom4j.io.XMLWriter;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.iso_relax.verifier.VerifierFactory;
import org.iso_relax.verifier.VerifierHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.function.Function;

public class XmlHelper {
    public enum Dir {
        IN, OUT
    }

    public static String getDirPrefix(Dir dir) {
        switch (dir) {
            case IN:
                return "in.";
            case OUT:
                return "out.";
            default:
                throw new InternalError("Unhandled case " + dir.name());
        }
    }

    public static void loadAttributes(Message.Builder builder, Element node, Dir dir) {
        final String prefix = getDirPrefix(dir);
        for (Descriptors.FieldDescriptor fieldDescriptor : builder.getDescriptorForType().getFields()) {
            final String value = node.attributeValue(prefix + fieldDescriptor.getName());

            // todo: this works for STRING only
            builder.setField(fieldDescriptor, value);
        }
    }

    public static void pasteAttributes(Element node, Message message, Dir dir) {
        final String prefix = getDirPrefix(dir);
        for (Descriptors.FieldDescriptor fieldDescriptor : message.getDescriptorForType().getFields()) {
            node.addAttribute(prefix + fieldDescriptor.getName(), message.getField(fieldDescriptor).toString());
        }
    }

    public static String asXML(Element element) {
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


    static String readFileFully(Path fileName) throws IOException {
        return new String(Files.readAllBytes(fileName), StandardCharsets.UTF_8);
    }


    public static Function<String, String> fileXSDFactory(String xsdPath)
    {
        return typeName -> {
            final Path filePath = Paths.get(xsdPath, typeName + ".xsd");

            try {
                return readFileFully(filePath);
            } catch (Exception e) {
                throw new RuntimeException("file not found " + filePath.toString(), e);
            }
        };
    }

    public static void changeNamespace(Document document, String fromNS, String toNS, String nsURI)
    {
        class NamesapceChangingVisitor extends VisitorSupport
        {
            private Namespace from;
            private Namespace to;

            public NamesapceChangingVisitor(Namespace from, Namespace to) {
                this.from = from;
                this.to = to;
            }

            public void visit(Element node) {
                Namespace ns = node.getNamespace();

                if (ns.getURI().equals(from.getURI())) {
                    QName newQName = new QName(node.getName(), to);
                    node.setQName(newQName);
                }

                ListIterator namespaces = node.additionalNamespaces().listIterator();
                while (namespaces.hasNext()) {
                    Namespace additionalNamespace = (Namespace) namespaces.next();
                    if (additionalNamespace.getURI().equals(from.getURI())) {
                        namespaces.remove();
                    }
                }
            }
        }

        Namespace oldNs = Namespace.get(fromNS);
        Namespace newNs = Namespace.get(toNS, nsURI);

        Visitor visitor = new NamesapceChangingVisitor(oldNs, newNs);
        document.accept(visitor);
    }


    public static void xmlValidate(String xmlText, Function<String, String> getSchema) throws IOException, SAXException, VerifierConfigurationException, DocumentException {
        xmlValidate(DocumentHelper.parseText(xmlText), getSchema);
    }

    public static void xmlValidate(Document document, Function<String, String> getSchema) throws IOException, SAXException, VerifierConfigurationException, DocumentException {
        final Element rootElement = document.getRootElement();
        final String rootElementName = rootElement.getName();

        final VerifierFactory factory = new TheFactoryImpl();
        final String schemaData = getSchema.apply(rootElementName);

        if (rootElement.getNamespaceURI().isEmpty()) {
            final Document xsdDocument = DocumentHelper.parseText(schemaData);

            final Element xsdRootElement = xsdDocument.getRootElement();
            final String targetNamespace = xsdRootElement.attributeValue("targetNamespace");
            if (!targetNamespace.isEmpty())
                changeNamespace(document, "", "", targetNamespace);
        }
        final org.iso_relax.verifier.Schema schema = factory.compileSchema(new ByteArrayInputStream(schemaData.getBytes(StandardCharsets.UTF_8)), rootElement.getNamespaceURI());

        org.iso_relax.verifier.Verifier verifier = schema.newVerifier();
//        verifier.setErrorHandler(new ErrorHandler() {
//            public void error(SAXParseException e) {
//                System.out.println("ERROR: " + e);
//            }
//
//            public void fatalError(SAXParseException e) {
//                System.out.println("FATAL: " + e);
//            }
//
//            public void warning(SAXParseException e) {
//                System.out.println("WARNING: " + e);
//            }
//        });

        VerifierHandler handler = verifier.getVerifierHandler();
        SAXWriter writer = new SAXWriter(handler);
        writer.write(document);
    }
}
