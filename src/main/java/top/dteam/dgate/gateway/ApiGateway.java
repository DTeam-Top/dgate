package top.dteam.dgate.gateway;

import groovy.lang.Closure;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import top.dteam.dgate.config.ApiGatewayConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dteam.dgate.config.Consumer;
import top.dteam.dgate.config.EventBusBridgeConfig;
import top.dteam.dgate.config.Publisher;

import java.util.List;
import java.util.Map;

public class ApiGateway extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(ApiGateway.class);

    private ApiGatewayConfig config;

    public ApiGateway(ApiGatewayConfig config) {
        this.config = config;
    }

    @Override
    public void start() {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = RouterBuilder.build(vertx, config);

        EventBusBridgeConfig eventBusBridgeConfig = config.getEventBusBridgeConfig();
        if (eventBusBridgeConfig != null) {
            buildEventBusBridge(eventBusBridgeConfig.getUrlPattern(), router);
        }

        httpServer.requestHandler(router::accept).listen(config.getPort(), config.getHost(), result -> {
            if (result.succeeded()) {
                if (eventBusBridgeConfig != null) {
                    EventBus eventBus = vertx.eventBus();
                    registerConsumers(eventBus, eventBusBridgeConfig.getConsumers());
                    registerPublishers(eventBus, eventBusBridgeConfig.getPublishers());
                }

                logger.info("API Gateway {} is listening at {}:{} ...",
                        config.getName(), config.getHost(), config.getPort());
            }
        });
    }

    private void buildEventBusBridge(String urlPattern, Router router) {
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        PermittedOptions allAllowed = new PermittedOptions().setAddressRegex(".*");
        router.route(urlPattern).handler(sockJSHandler.bridge(new BridgeOptions()
                .addInboundPermitted(allAllowed)
                .addOutboundPermitted(allAllowed)));
    }

    private void registerConsumers(EventBus eventBus, List<Consumer> consumers) {
        consumers.forEach(consumer -> eventBus.consumer(consumer.getAddress(), message -> {
            if (consumer.getTarget() == null) {
                message.reply(transformIfNeeded(consumer.getExpected(), message));
            } else {
                eventBus.publish(consumer.getTarget(), transformIfNeeded(consumer.getExpected(), message));
            }
        }));
    }

    private void registerPublishers(EventBus eventBus, List<Publisher> publishers) {
        publishers.forEach(publisher ->
                vertx.setPeriodic(publisher.getTimer(), tid -> eventBus.publish(publisher.getTarget()
                        , transformIfNeeded(publisher.getExpected(), null)))
        );
    }

    private JsonObject transformIfNeeded(Object expected, Message<Object> message) {
        JsonObject result;
        if (expected instanceof Closure) {
            result = (message == null) ? new JsonObject((Map) ((Closure) expected).call())
                    : new JsonObject((Map) ((Closure) expected).call(message));
        } else {
            result = new JsonObject((Map) expected);
        }

        logger.debug("{}", result);

        return result;
    }
}
