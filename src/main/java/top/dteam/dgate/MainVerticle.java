package top.dteam.dgate;

import top.dteam.dgate.config.ApiGatewayRepository;
import top.dteam.dgate.gateway.ApiGateway;
import io.vertx.core.AbstractVerticle;
import top.dteam.dgate.monitor.CircuitBreakerMonitor;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {
        vertx.deployVerticle( new CircuitBreakerMonitor());

        ApiGatewayRepository repository = ApiGatewayRepository.load();
        repository.getRespository().stream()
                .forEach(apiGatewayConfig -> vertx.deployVerticle(new ApiGateway(apiGatewayConfig)));

    }
}
