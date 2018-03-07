package com.luxoft.xmlcall.handler;

import com.luxoft.xmlcall.fabric.XmlCallFabricConnector;

import java.lang.reflect.Constructor;

public class XmlCallBlockchainConnectorFactory
{
    static XmlCallBlockchainConnectorFactory instance = new XmlCallBlockchainConnectorFactory();

    private XmlCallBlockchainConnectorFactory()
    {}

    public static XmlCallBlockchainConnectorFactory getInstance()
    {
        return instance;
    }

    public XmlCallBlockchainConnector newConnection(String connectorClass, String connectorArg) throws Exception {
        if ("XmlCallFabricConnector".equals(connectorClass))
            return XmlCallFabricConnector.newInstance(connectorArg);

        final Class<?> aClass = Thread.currentThread().getContextClassLoader().loadClass(connectorClass);

        Constructor<?> constructor;

        try {
            constructor = aClass.getConstructor();
            return (XmlCallBlockchainConnector) constructor.newInstance();
        }

        catch (NoSuchMethodException e)
        {
        }

        try {
            constructor = aClass.getConstructor(String.class);
            return (XmlCallBlockchainConnector) constructor.newInstance(connectorArg);
        }
        catch (NoSuchMethodException e)
        {
        }

        throw new RuntimeException("Unable to find suitable constructor in " + connectorClass);
    }

}
