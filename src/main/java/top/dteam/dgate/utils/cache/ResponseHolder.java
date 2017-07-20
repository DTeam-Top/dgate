package top.dteam.dgate.utils.cache;

import io.vertx.core.json.JsonObject;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.eviction.lru.LruEvictionPolicy;
import org.apache.ignite.configuration.CacheConfiguration;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ResponseHolder {
    private static final int MAX_ENTRY_PER_CACHE = 1000;
    private static ConcurrentHashMap<String, CacheConfiguration<String
            , JsonObject>> cacheCfgHolder = new ConcurrentHashMap<>();

    private static IgniteCache<String, JsonObject> getOrCreate(
            String apiGatewayName, String route, int expires) {
        String cacheName = cacheName(apiGatewayName, route);

        CacheConfiguration<String, JsonObject> cacheCfg;
        if (cacheCfgHolder.contains(cacheName)) {
            cacheCfg = cacheCfgHolder.get(cacheName);
        } else {
            cacheCfg = new CacheConfiguration<>(cacheName);
            cacheCfg.setCacheMode(CacheMode.LOCAL);
            cacheCfg.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(
                    new Duration(TimeUnit.MILLISECONDS, expires)));
            cacheCfg.setEagerTtl(false);
            cacheCfg.setEvictionPolicy(new LruEvictionPolicy(MAX_ENTRY_PER_CACHE));

            cacheCfgHolder.put(cacheName, cacheCfg);
        }

        return CacheLocator.getOrCreateCache(cacheCfg);
    }

    public static void put(String apiGatewayName, String route
            , String URL, JsonObject token, JsonObject content, int expires) {
        IgniteCache<String, JsonObject> cache =
                getOrCreate(apiGatewayName, route, expires);
        String tokenString = token != null ? token.toString() : null;
        String cacheKey = cacheKey(URL, tokenString);

        cache.put(cacheKey, content);
    }

    public static void put(String apiGatewayName, String route
            , String upstreamHost, int upstreamPort, String upstreamURL
            , String URL, JsonObject token, JsonObject content, int expires) {
        String upstreamRoute = upstreamRoute(route, upstreamHost, upstreamPort, upstreamURL);

        put(apiGatewayName, upstreamRoute, URL, token, content, expires);
    }

    public static JsonObject get(String apiGatewayName, String route
            , String URL, JsonObject token) {
        String cacheName = cacheName(apiGatewayName, route);
        if (CacheLocator.containsCache(cacheName)) {
            IgniteCache<String, JsonObject> cache =
                    CacheLocator.getCacheByName(cacheName);
            String tokenString = token != null ? token.toString() : null;

            return cache.get(cacheKey(URL, tokenString));
        } else {
            return null;
        }
    }

    public static JsonObject get(String apiGatewayName, String route
            , String upstreamHost, int upstreamPort, String upstreamURL
            , String URL, JsonObject token) {
        String upstreamRoute = upstreamRoute(route, upstreamHost, upstreamPort, upstreamURL);

        return get(apiGatewayName, upstreamRoute, URL, token);
    }

    public static boolean containsCacheName(String apiGatewayName, String route) {
        return CacheLocator.containsCache(cacheName(apiGatewayName, route));
    }

    public static boolean containsCacheEntry(String apiGatewayName, String route
            , String URL, JsonObject token) {
        if (containsCacheName(apiGatewayName, route)) {
            IgniteCache<String, JsonObject> cache =
                    CacheLocator.getCacheByName(cacheName(apiGatewayName, route));
            String tokenString = token != null ? token.toString() : null;
            String cacheKey = cacheKey(URL, tokenString);

            return (cache.containsKey(cacheKey) && cache.get(cacheKey) != null);
        } else {
            return false;
        }
    }

    public static boolean containsCacheEntry(String apiGatewayName, String route
            , String upstreamHost, int upstreamPort, String upstreamURL
            , String URL, JsonObject token) {
        String upstreamRoute = upstreamRoute(route, upstreamHost, upstreamPort, upstreamURL);

        return containsCacheEntry(apiGatewayName, upstreamRoute, URL, token);
    }

    private static String cacheName(String apiGatewayName, String route) {
        return apiGatewayName + route;
    }

    private static String cacheKey(String requestURI, String token) {
        return token == null ? requestURI : requestURI + "-" + token;
    }

    private static String upstreamRoute(String route, String upstreamHost
            , int upstreamPort, String upstreamURL) {
        return route + "-" + upstreamHost + ":" + upstreamPort + upstreamURL;
    }
}
