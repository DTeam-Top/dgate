package top.dteam.dgate.gateway;

import top.dteam.dgate.config.ApiGatewayConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiGateway extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(ApiGateway.class);

    ApiGatewayConfig config;

    public ApiGateway(ApiGatewayConfig config) {
        this.config = config;
    }

    @Override
    public void start() {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = RouterBuilder.builder(vertx, config);
        httpServer.requestHandler(router::accept).listen(config.getPort(), result -> {
            if (result.succeeded()) {
                logger.info("API Gateway {} is listening at {} ...", config.getName(), config.getPort());
            }
        });
    }
}
