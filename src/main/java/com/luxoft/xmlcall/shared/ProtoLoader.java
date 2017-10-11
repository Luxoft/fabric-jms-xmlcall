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
}
