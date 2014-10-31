package flipkart.mongo.replicator.cluster;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import flipkart.mongo.node.discovery.scheduler.ClusterDiscoveryScheduler;
import flipkart.mongo.replicator.core.interfaces.IReplicationHandler;
import flipkart.mongo.replicator.core.interfaces.VersionHandler;
import flipkart.mongo.replicator.core.model.MongoV;
import flipkart.mongo.replicator.core.model.ReplicationEvent;
import flipkart.mongo.replicator.core.versions.VersionManager;
import flipkart.mongo.replicator.node.ReplicaSetManager;
import flipkart.mongo.replicator.node.ReplicaSetReplicator;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created by pradeep on 29/10/14.
 */
public class ClusterReplicator extends AbstractService {

    public final ClusterManager clusterManager;
    public final IReplicationHandler replicationHandler;
    public final VersionHandler versionHandler;
    private final MongoV version;
    private final Function<ReplicationEvent, Boolean> oplogFilter;
    private ScheduledExecutorService scheduler;
    private ServiceManager replicasReplicatorServiceManager;

    public ClusterReplicator(ClusterManager clusterManager, IReplicationHandler replicationHandler, MongoV version, Function<ReplicationEvent, Boolean> oplogFilter) {
        this.clusterManager = clusterManager;
        this.replicationHandler = replicationHandler;
        this.version = version;
        versionHandler = VersionManager.singleton().getVersionHandler(version);
        this.oplogFilter = oplogFilter;
    }

    @Override
    protected void doStart() {
        ImmutableList<ReplicaSetManager> replSetManagers = clusterManager.getReplicaSetManagers();
        Set<Service> services = new LinkedHashSet<Service>();
        for (ReplicaSetManager replSetManager : replSetManagers) {
            services.add(new ReplicaSetReplicator(replSetManager, replicationHandler, version, oplogFilter));
        }

        replicasReplicatorServiceManager = new ServiceManager(services);
        replicasReplicatorServiceManager.addListener(new ServiceManager.Listener() {
            @Override
            public void healthy() {

            }

            @Override
            public void stopped() {

            }

            @Override
            public void failure(Service service) {
                service.startAsync();
            }
        });

        replicasReplicatorServiceManager.startAsync();
        scheduler = attachScheduler();
    }

    @Override
    protected void doStop() {
        // stopping the scheduler
        if (scheduler != null)
            scheduler.shutdown();

        replicasReplicatorServiceManager.stopAsync();
    }

    private ScheduledExecutorService attachScheduler() {
        //TODO: Need to get from config builder
        long initialDelay = 10;
        long periodicDelay = 5;

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        ClusterDiscoveryScheduler clusterDiscoveryScheduler = new ClusterDiscoveryScheduler(clusterManager.cluster.cfgsvrs);
        // registering clusterDiscovery for config updates
        clusterDiscoveryScheduler.registerCallback(clusterManager);

        // starting the scheduler
        scheduler.scheduleWithFixedDelay(clusterDiscoveryScheduler, initialDelay, periodicDelay, TimeUnit.SECONDS);

        return scheduler;
    }
}
