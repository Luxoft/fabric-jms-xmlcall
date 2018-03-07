package com.luxoft.xmlcall.reflect;

import com.luxoft.xmlcall.handler.XmlCallBlockchainConnector;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class XmlCallReflectionConnector implements XmlCallBlockchainConnector
{
    private final Map<String, Object> instance = new HashMap<>();
    private final String namespace;

    public XmlCallReflectionConnector(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public CompletableFuture<Result> exec(ExecType execType,
                                          String channel,
                                          String chaincodeId,
                                          String chaincodeName,
                                          String methodName,
                                          byte[][] args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final Object obj = instance.computeIfAbsent(chaincodeName, s -> {
                    try {
                        final Class<?> aClass = ClassLoader.getSystemClassLoader().loadClass(namespace + "." + chaincodeName);
                        return aClass.newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed", e);
                    }
                });

                Method method = obj.getClass().getMethod(methodName, byte[][].class);
                final byte[] bytes = (byte[])method.invoke(obj, (Object)args);

                return new Result(UUID.randomUUID().toString(), bytes);

            } catch (Exception e) {
                throw new RuntimeException("Failed", e);
            }
        });
    }

    public static XmlCallReflectionConnector newInstance(String namespace)
    {
        return new XmlCallReflectionConnector(namespace);
    }
}
