package top.dteam.dgate.utils.cache;

import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;

import java.util.UUID;

public class CacheLocator {
    private static Ignite ignite;

    public static void init(Vertx vertx) {
        // Get ignite instance from vertx instance
        if (ignite == null) {
            ClusterManager clusterManager = ((VertxInternal) vertx).getClusterManager();
            String uuid = clusterManager.getNodeID();
            ignite = Ignition.ignite(UUID.fromString(uuid));
        }
    }

    public static void close() {
        if (ignite != null) {
            ignite.close();
            ignite = null;
        }
    }

    static <K, V> IgniteCache<K, V> getCacheByName(String cacheName) {
        return ignite.cache(cacheName);
    }

    static boolean containsCache(String cacheName) {
        return ignite.cacheNames().contains(cacheName);
    }

    static <K, V> IgniteCache<K, V> getOrCreateCache(CacheConfiguration<K, V> cacheCfg) {
        return ignite.getOrCreateCache(cacheCfg);
    }
}
