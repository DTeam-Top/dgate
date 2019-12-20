package top.dteam.dgate;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.spi.cluster.ignite.IgniteClusterManager;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dteam.dgate.utils.cache.CacheLocator;

import java.util.Arrays;

public class Launcher extends io.vertx.core.Launcher {
    private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {

        //Force to use slf4j
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");

        new Launcher().dispatch(args);
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
        System.setProperty("IGNITE_NO_SHUTDOWN_HOOK", "true");
        options.setClusterManager(new IgniteClusterManager(igniteConfiguration()));
        options.getEventBusOptions().setClustered(true);
    }

    @Override
    public void afterStartingVertx(Vertx vertx) {
        CacheLocator.init(vertx);
    }

    private static IgniteConfiguration igniteConfiguration() {
        // static IP Based Discovery, see: https://apacheignite.readme.io/docs/cluster-config#section-static-ip-based-discovery
        String clusterNodes = System.getenv("DGATE_CLUSTER_NODES") == null ?
                "localhost" : System.getenv("DGATE_CLUSTER_NODES");

        TcpDiscoverySpi spi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();

        ipFinder.setAddresses(Arrays.asList(clusterNodes.split(",")));
        spi.setIpFinder(ipFinder);
        IgniteConfiguration cfg = new IgniteConfiguration();

        // Override default discovery SPI.
        cfg.setDiscoverySpi(spi);

        logger.info("Dgate is working on ip based cluster mode. " +
                "Cluster ip list: {}", clusterNodes);

        return cfg;
    }
}
