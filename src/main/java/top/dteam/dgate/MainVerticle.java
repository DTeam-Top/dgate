package top.dteam.dgate;

import io.vertx.core.AbstractVerticle;
import top.dteam.dgate.config.ApiGatewayRepository;
import top.dteam.dgate.gateway.ApiGateway;
import top.dteam.dgate.monitor.CircuitBreakerMonitor;
import top.dteam.dgate.utils.cache.CacheLocator;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {
        vertx.deployVerticle(new CircuitBreakerMonitor());

        ApiGatewayRepository.load();
        ApiGatewayRepository.getRespository().stream()
                .forEach(apiGatewayConfig -> vertx.deployVerticle(new ApiGateway(apiGatewayConfig)));

    }

    @Override
    public void stop() {
        CacheLocator.close();
    }
}
