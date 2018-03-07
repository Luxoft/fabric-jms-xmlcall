package com.luxoft.xmlcall.shared;

import com.google.protobuf.*;
import com.luxoft.xmlcall.proto.XmlCall;

import java.util.*;

public class ProtoLoader
{
    private static class FileOption<T>
    {
        final ExtensionLite<com.google.protobuf.DescriptorProtos.FileOptions, T> extensionLite;
        Descriptors.FileDescriptor definedIn = null;
        T value = null;

        FileOption(ExtensionLite<com.google.protobuf.DescriptorProtos.FileOptions, T> extensionLite, T defValue)
        {
            this.extensionLite = extensionLite;
            this.value = defValue;
        }

        void update(Descriptors.FileDescriptor fileDescriptor)
        {
            if (!fileDescriptor.getOptions().hasExtension(extensionLite))
                return;

            final T s = fileDescriptor.getOptions().getExtension(extensionLite);

            value = s;
            definedIn = fileDescriptor;
        }

        T getValue()
        {
            return value;
        }

        public Descriptors.FileDescriptor getSource() {
            return definedIn;
        }
    }

    private final FileOption<String> namespaceURI = new FileOption<>(XmlCall.namespace, "");
    private final FileOption<String> faultTypeName = new FileOption<>(XmlCall.faultType, "");
    private final FileOption<String> requestAttributes = new FileOption<>(XmlCall.requestAttributes, "");
    private final FileOption<String> resultAttributes = new FileOption<>(XmlCall.resultAttributes, "");
    private final DescriptorProtos.FileDescriptorSet fileDescriptorSet;
    private final HashMap<String, Descriptors.FileDescriptor> fileDescriptors = new HashMap<>();
    private final ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
    private final Set<Descriptors.ServiceDescriptor> serviceDescriptors = new HashSet<>();
    private final Descriptors.Descriptor faultType;
    private final Descriptors.Descriptor requestAttributesType;
    private final Descriptors.Descriptor resultAttributesType;

    public ProtoLoader(String fileName) throws Exception
    {
        final byte[] bytes = XmlHelper.readFileAsBytes(fileName);

        XmlCall.registerAllExtensions(extensionRegistry);

        fileDescriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(bytes, extensionRegistry);
        final Descriptors.FileDescriptor[] t = {};
        List<Descriptors.FileDescriptor> deps = new ArrayList<>();

        for (DescriptorProtos.FileDescriptorProto fileDescriptorProto : fileDescriptorSet.getFileList()) {
            final Descriptors.FileDescriptor[] fileDescriptors = deps.toArray(t);
            final Descriptors.FileDescriptor fileDescriptor
                    = Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, fileDescriptors);

            namespaceURI.update(fileDescriptor);
            faultTypeName.update(fileDescriptor);
            resultAttributes.update(fileDescriptor);
            requestAttributes.update(fileDescriptor);

            this.fileDescriptors.put(fileDescriptorProto.getName(), fileDescriptor);

            deps.add(fileDescriptor);

            for (Descriptors.ServiceDescriptor serviceDescriptor : fileDescriptor.getServices()) {
                this.serviceDescriptors.add(serviceDescriptor);
            }
        }

        faultType = getAnchoredType(faultTypeName.getValue(), faultTypeName.getSource());
        resultAttributesType = getAnchoredType(resultAttributes.getValue(), resultAttributes.getSource());
        requestAttributesType = getAnchoredType(requestAttributes.getValue(), requestAttributes.getSource());
    }

    public HashMap<String, Descriptors.FileDescriptor> getFileDescriptors()
    {
        return fileDescriptors;
    }

    public ExtensionRegistry getExtensionRegistry()
    {
        return extensionRegistry;
    }

    public Set<Descriptors.ServiceDescriptor> getServices()
    {
        return serviceDescriptors;
    }

    public String getNamespaceURI() {
        return namespaceURI.getValue();
    }

    private Descriptors.Descriptor getAnchoredType(String name, Descriptors.FileDescriptor fileDescriptor)
    {
        if (name == null || name.isEmpty())
            return Empty.getDescriptor();

        if (name.startsWith("."))
            return getType(name);

        final String filePackage = fileDescriptor.getPackage();
        if (name.startsWith(filePackage + "."))
            name = name.substring(filePackage.length() + 1);

        return fileDescriptor.findMessageTypeByName(name);
    }

    public Descriptors.Descriptor getType(String name)
    {
        final boolean anchored = name.startsWith(".");
        final int index = name.lastIndexOf('.');
        String reqPackage;
        String reqName;

        if (index != -1) {
            reqPackage = name.substring(anchored ? 1 : 0, index);
            reqName = name.substring(index + 1);
        }
        else {
            reqPackage = "";
            reqName = name;
        }

        for (Descriptors.FileDescriptor fileDescriptor : fileDescriptors.values()) {

            /* check package prefix first */
            if (!reqPackage.isEmpty()) {
                final String filePackage = fileDescriptor.getPackage();

                if (anchored) {
                    if (!filePackage.equals(reqPackage))
                        continue;
                }

                else {
                    if (!filePackage.endsWith(reqPackage))
                        continue;

                    if (filePackage.length() != reqPackage.length() && filePackage.charAt(filePackage.length() - reqPackage.length() - 1) != '.')
                        continue;
                }
            }

            final Descriptors.Descriptor messageType = fileDescriptor.findMessageTypeByName(reqName);
            if (messageType != null)
                return messageType;
        }

        throw new RuntimeException("type " + name + " not found");
    }

    public Descriptors.Descriptor getFaultType() {
        return faultType;
    }

    public Descriptors.Descriptor getRequestAttributes() {
        return requestAttributesType;
    }

    public Descriptors.Descriptor getResultAttributes() {
        return resultAttributesType;
    }
}
