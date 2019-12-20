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
import top.dteam.dgate.utils.cache.ResponseHolder;

import java.util.Base64;

public class RelayHandler implements GatewayRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RelayHandler.class);

    private Vertx vertx;
    private RelayUrlConfig urlConfig;
    private RelayTo relayTo;
    private String nameOfApiGateway;
    private RequestUtils requestUtils;
    private CircuitBreaker circuitBreaker;

    public RelayHandler(Vertx vertx, RelayUrlConfig urlConfig) {
        this.vertx = vertx;
        this.urlConfig = urlConfig;
        this.relayTo = urlConfig.getRelayTo();
        this.requestUtils = new RequestUtils(vertx);
        if (relayTo.getCircuitBreaker() != null) {
            this.circuitBreaker = CircuitBreaker.create(String.format("cb-%s-%s", urlConfig.getUrl(),
                    relayTo.toString()), vertx, relayTo.getCircuitBreaker());
        } else {
            this.circuitBreaker = CircuitBreaker.create(String.format("cb-%s-%s", urlConfig.getUrl(),
                    relayTo.toString()), vertx);
        }
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        JsonObject token = getJwtTokenFromRoutingContext(routingContext);

        if (isResponseCached(request.uri(), token)) {
            logger.info("Found response cache for {}/{}"
                    , nameOfApiGateway, urlConfig.getUrl());
            SimpleResponse simpleResponse = new SimpleResponse();
            simpleResponse.setStatusCode(200);
            simpleResponse.setPayload(getResponseFromCache(request.uri(), token));

            Utils.fireJsonResponse(routingContext.response(), simpleResponse.getStatusCode(),
                    simpleResponse.getPayload().getMap());

            return;
        }

        try {
            circuitBreaker.execute(future -> {
                HttpClientRequest relay = requestUtils.relay(request.method()
                        , relayTo.getHost(), relayTo.getPort(), request.uri()
                        , future::complete);

                relay.headers().addAll(request.headers());

                RequestUtils.putProxyHeaders(relay, request);

                putJwtTokenToHeader(relay, routingContext);
                putNameOfApiGatewayInBody(relay, nameOfApiGateway);

                Pump pump = Pump.pump(request, relay);
                request.endHandler(end -> relay.end());
                pump.start();
            }).setHandler(result -> {
                SimpleResponse simpleResponse;
                if (result.succeeded()) {
                    simpleResponse = (SimpleResponse) result.result();

                    if (urlConfig.getExpires() > 0) {
                        logger.info("Put response cache for {}{}."
                                , nameOfApiGateway, urlConfig.getUrl());

                        putResponseToCache(request.uri(), token
                                , simpleResponse.getPayload()
                                , urlConfig.getExpires());
                    }
                } else {
                    logger.error("CB[{}] execution failed, cause: ", circuitBreaker.name(), result.cause());

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

    private JsonObject getJwtTokenFromRoutingContext(RoutingContext routingContext) {
        JsonObject token = null;
        if (routingContext.user() != null) {
            token = routingContext.user().principal();
        } else if (routingContext.get("token") != null) {
            token = routingContext.get("token");
        }

        return token;
    }

    private void putJwtTokenToHeader(HttpClientRequest request, RoutingContext routingContext) {
        JsonObject token = getJwtTokenFromRoutingContext(routingContext);

        if (token != null) {
            request.putHeader(RequestUtils.JWT_HEADER, Base64.getEncoder().encodeToString(token.toString().getBytes()));
        }
    }

    private void putNameOfApiGatewayInBody(HttpClientRequest request, String nameOfApiGateway) {
        if (nameOfApiGateway != null) {
            request.putHeader(RequestUtils.API_GATEWAY_NAME_HEADER
                    , Base64.getEncoder().encodeToString(nameOfApiGateway.getBytes()));
        }
    }

    private boolean isResponseCached(String requestURI, JsonObject token) {
        return urlConfig.getExpires() > 0 &&
                ResponseHolder.containsCacheEntry(nameOfApiGateway
                        , urlConfig.getUrl(), requestURI, token);
    }

    private JsonObject getResponseFromCache(String requestURI, JsonObject token) {
        return ResponseHolder.get(nameOfApiGateway, urlConfig.getUrl(), requestURI, token);
    }

    private void putResponseToCache(String requestURI, JsonObject token
            , JsonObject response, int expires) {
        ResponseHolder.put(nameOfApiGateway, urlConfig.getUrl(), requestURI, token, response, expires);
    }
}
