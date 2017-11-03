package rules;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.broker.jmx.BrokerView;
import org.apache.activemq.broker.jmx.ManagedRegionBroker;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.usage.SystemUsage;
import org.junit.rules.ExternalResource;
import org.apache.activemq.broker.region.Queue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class ActiveMQBrokerRule extends ExternalResource {
    private static final int STORAGE_LIMIT = 1024 * 1024 * 8; // 8mb
    private final String endpoint; // vm://localhost or tcp://localhost:0

    private BrokerService broker;

    public ActiveMQBrokerRule(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    protected void before() throws Throwable {
        broker = new BrokerService();
        broker.addConnector(connector());
        broker.setUseJmx(true);
        broker.setPersistent(false);
        configureStorage();
        broker.start();
    }

    @Override
    protected void after() {
        if (broker != null) {
            try {
                broker.stop();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

//    public ActiveMQJMXUtils getActiveMQJMXUtils() {
//        return new ActiveMQJMXUtils(this.broker);
//    }

    private TransportConnector connector() throws URISyntaxException {
        TransportConnector connector = new TransportConnector();
        connector.setUri(new URI(endpoint));
        return connector;
    }

    private void configureStorage() {
        SystemUsage systemUsage = broker.getSystemUsage();
        systemUsage.getStoreUsage().setLimit(STORAGE_LIMIT);
        systemUsage.getTempUsage().setLimit(STORAGE_LIMIT);
    }

    public void purgeQueue(String queueName) throws Exception {
        final ActiveMQQueue activeMQQueue = new ActiveMQQueue(queueName);
        BrokerView adminView = broker.getAdminView();
        final ManagedRegionBroker managedRegionBroker = adminView.getBroker();
        final Set<Destination> destinations = managedRegionBroker.getQueueRegion().getDestinations(activeMQQueue);
        for (Destination destination : destinations) {
            if (destination instanceof Queue) {
                Queue regionQueue = (Queue) destination;
                regionQueue.purge();
            }
        }
    }

}
