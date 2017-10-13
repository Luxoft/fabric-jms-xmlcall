package com.luxoft.xmlcall.shared;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ExtensionRegistry;
import com.luxoft.xmlcall.proto.XmlCall;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ProtoLoader
{
    private final DescriptorProtos.FileDescriptorSet fileDescriptorSet;
    private final HashMap<String, Descriptors.FileDescriptor> fileDescriptors = new HashMap<>();
    private final ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
    private final List<Descriptors.ServiceDescriptor> serviceDescriptors = new ArrayList<>();

    public ProtoLoader(String fileName) throws Exception
    {
        final byte[] bytes = Files.readAllBytes(Paths.get(fileName));

        XmlCall.registerAllExtensions(extensionRegistry);

        fileDescriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(bytes, extensionRegistry);
        final Descriptors.FileDescriptor[] t = {};
        List<Descriptors.FileDescriptor> deps = new ArrayList<>();

        for (DescriptorProtos.FileDescriptorProto fileDescriptorProto : fileDescriptorSet.getFileList()) {
            final Descriptors.FileDescriptor[] fileDescriptors = deps.toArray(t);
            final Descriptors.FileDescriptor fileDescriptor
                    = Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, fileDescriptors);

            this.fileDescriptors.put(fileDescriptorProto.getName(), fileDescriptor);
            deps.add(fileDescriptor);

            for (Descriptors.ServiceDescriptor serviceDescriptor : fileDescriptor.getServices()) {
                this.serviceDescriptors.add(serviceDescriptor);
            }
        }

    }

    public HashMap<String, Descriptors.FileDescriptor> getFileDescriptors()
    {
        return fileDescriptors;
    }

    public ExtensionRegistry getExtensionRegistry()
    {
        return extensionRegistry;
    }

    public List<Descriptors.ServiceDescriptor> getServices()
    {
        return serviceDescriptors;
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
}
