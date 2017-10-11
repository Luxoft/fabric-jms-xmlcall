import com.luxoft.xmlcall.handler.XmlCallBlockchainConnector;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class XmlCallReflectionConnector implements XmlCallBlockchainConnector
{
    private final Map<String, Object> instance = new HashMap<>();
    private final String namespace;

    public XmlCallReflectionConnector(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public CompletableFuture<byte[]> exec(ExecType execType,
                                          String channelName,
                                          String chaincodeName,
                                          String chaincodeId,
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
                return (byte[])method.invoke(obj, (Object)args);

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
