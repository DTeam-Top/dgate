package top.dteam.dgate.handler;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dteam.dgate.config.RelayTo;
import top.dteam.dgate.config.RelayUrlConfig;
import top.dteam.dgate.gateway.SimpleResponse;
import top.dteam.dgate.utils.RequestUtils;
import top.dteam.dgate.utils.Utils;

public class RelayHandler implements GatewayRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RelayHandler.class);

    private Vertx vertx;
    private RelayTo relayTo;
    private String nameOfApiGateway;
    private RequestUtils requestUtils;
    private CircuitBreaker circuitBreaker;

    public RelayHandler(Vertx vertx, RelayUrlConfig urlConfig) {
        this.vertx = vertx;
        this.relayTo = urlConfig.getRelayTo();
        this.requestUtils = new RequestUtils(vertx);
        this.circuitBreaker = CircuitBreaker.create(String.format("cb-%s-%s", urlConfig.getUrl(),
                relayTo.toString()), vertx, relayTo.getCbOptions());
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            circuitBreaker.execute(future -> {
                HttpServerRequest request = routingContext.request();
                HttpClientRequest relay = requestUtils.relay(request.method()
                        , relayTo.getHost(), relayTo.getPort(), request.uri()
                        , simpleResponse -> future.complete(simpleResponse));

                relay.headers().addAll(request.headers());
                Pump pump = Pump.pump(request, relay);
                request.endHandler(Void -> relay.end());
                pump.start();
            }).setHandler(result -> {
                SimpleResponse simpleResponse;
                if (result.succeeded()) {
                    simpleResponse = (SimpleResponse) result.result();
                } else {
                    logger.error("CB[{}] execution failed, cause: {}", circuitBreaker.name(), result.cause());

                    simpleResponse = new SimpleResponse();
                    JsonObject error = new JsonObject();
                    error.put("error", result.cause().getMessage());
                    simpleResponse.setPayload(error);
                    simpleResponse.setStatusCode(500);
                }

                Utils.fireJsonResponse(routingContext.response(), simpleResponse.getStatusCode(),
                        simpleResponse.getPayload().getMap());
            });
        } catch (Exception e) {
            logger.error(e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public GatewayRequestHandler nameOfApiGateway(String nameOfApiGateway) {
        this.nameOfApiGateway = nameOfApiGateway;
        return this;
    }
}
