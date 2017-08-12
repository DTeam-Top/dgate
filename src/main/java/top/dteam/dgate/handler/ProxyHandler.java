package top.dteam.dgate.handler;

import groovy.lang.Closure;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dteam.dgate.config.ProxyUrlConfig;
import top.dteam.dgate.config.UpstreamURL;
import top.dteam.dgate.gateway.SimpleResponse;
import top.dteam.dgate.utils.RequestUtils;
import top.dteam.dgate.utils.Utils;
import top.dteam.dgate.utils.cache.ResponseHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyHandler extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);

    private List<UpstreamURL> upstreamURLs;
    private Map<String, CircuitBreaker> circuitBreakers;
    private RequestUtils requestUtils;

    public ProxyHandler(Vertx vertx, ProxyUrlConfig urlConfig) {
        super(vertx, urlConfig);

        upstreamURLs = urlConfig.getUpstreamURLs();
        requestUtils = new RequestUtils(vertx);

        circuitBreakers = new HashMap<>();
        upstreamURLs.forEach(upStreamURL -> {
                    if (upStreamURL.getCircuitBreaker() != null) {
                        circuitBreakers.put(upStreamURL.toString(),
                                CircuitBreaker.create(String.format("cb-%s-%s", urlConfig.getUrl(),
                                        upStreamURL.toString()), vertx, upStreamURL.getCircuitBreaker()));
                    } else {
                        circuitBreakers.put(upStreamURL.toString(),
                                CircuitBreaker.create(String.format("cb-%s-%s", urlConfig.getUrl(),
                                        upStreamURL.toString()), vertx));
                    }
                }
        );
    }

    @Override
    protected void processRequestBody(HttpServerRequest request, HttpServerResponse response, JsonObject body) {
        List<CompletableFuture<SimpleResponse>> completableFutures = new ArrayList<>();
        upstreamURLs.forEach(upStreamURL -> {
            CompletableFuture<SimpleResponse> completableFuture = new CompletableFuture<>();
            partialRequest(request, upStreamURL, body, completableFuture);
            completableFutures.add(completableFuture);
        });

        List<Integer> statusCodes = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(completableFutures.size());
        JsonObject payload = new JsonObject();
        for (int i = 0; i < completableFutures.size(); i++) {
            UpstreamURL upStream = upstreamURLs.get(i);
            completableFutures.get(i).thenAccept(simpleResponse -> {
                payload.mergeIn(simpleResponse.getPayload());
                statusCodes.add(simpleResponse.getStatusCode());
                if (count.decrementAndGet() == 0) {
                    Utils.fireJsonResponse(response, finalStatusCode(statusCodes)
                            , payload.getMap());
                }
            }).exceptionally(throwable -> {
                payload.put(upStream.toString(), throwable);
                statusCodes.add(500);
                if (count.decrementAndGet() == 0) {
                    Utils.fireJsonResponse(response, finalStatusCode(statusCodes), payload.getMap());
                }
                return null;
            });
        }
    }

    private void partialRequest(HttpServerRequest clientRequest, UpstreamURL upstreamURL, JsonObject params,
                                CompletableFuture<SimpleResponse> completableFuture) {
        try {
            String requestURI = upstreamURL.resolve(params);

            if (isResponseCached(requestURI, upstreamURL, params.getJsonObject("token"))) {
                logger.info("Found response cache for {}{}{}"
                        , nameOfApiGateway, urlConfig.getUrl(), requestURI);
                SimpleResponse simpleResponse = new SimpleResponse();
                simpleResponse.setStatusCode(200);
                simpleResponse.setPayload(getResponseFromCache(requestURI
                        , upstreamURL, params.getJsonObject("token")));

                completableFuture.complete(simpleResponse);

                return;
            }

            CircuitBreaker circuitBreaker = circuitBreakers.get(upstreamURL.toString());
            circuitBreaker.execute(future -> {
                Map beforeContext = createBeforeContext();
                if (upstreamURL.getBefore() != null && beforeContext != null) {
                    upstreamURL.getBefore().setDelegate(beforeContext);
                }
                requestUtils.request(clientRequest.method(),
                        upstreamURL.getHost(), upstreamURL.getPort(), requestURI,
                        processParamsIfBeforeHandlerExists(upstreamURL.getBefore(), params), clientRequest,
                        simpleResponse -> {
                            Map afterContext = createAfterContext();
                            if (upstreamURL.getAfter() != null && afterContext != null) {
                                upstreamURL.getAfter().setDelegate(afterContext);
                            }
                            future.complete(processResponseIfAfterHandlerExists(upstreamURL.getAfter(), simpleResponse));
                        });
            }).setHandler(result -> {
                if (result.succeeded()) {
                    SimpleResponse simpleResponse = (SimpleResponse) result.result();
                    completableFuture.complete(simpleResponse);

                    if (upstreamURL.getExpires() > 0) {
                        logger.info("Put response cache for {}/{}{}"
                                , nameOfApiGateway, urlConfig.getUrl(), requestURI);
                        putResponseToCache(requestURI, upstreamURL
                                , params.getJsonObject("token")
                                , simpleResponse.getPayload()
                                , upstreamURL.getExpires());
                    }
                } else {
                    logger.error("CB[{}] execution failed, cause: ", circuitBreaker.name(), result.cause());

                    SimpleResponse simpleResponse = new SimpleResponse();
                    JsonObject error = new JsonObject();
                    error.put("error", result.cause().getMessage());
                    simpleResponse.setPayload(error);
                    simpleResponse.setStatusCode(500);

                    completableFuture.complete(simpleResponse);
                }
            });
        } catch (Exception e) {
            logger.error("Request to upstream failed: ", e);

            SimpleResponse simpleResponse = new SimpleResponse();
            JsonObject error = new JsonObject();
            error.put("error", e.getMessage());
            simpleResponse.setPayload(error);
            simpleResponse.setStatusCode(500);

            completableFuture.complete(simpleResponse);
        }
    }

    protected Map createBeforeContext() {
        return null;
    }

    protected Map createAfterContext() {
        return null;
    }

    private JsonObject processParamsIfBeforeHandlerExists(Closure<JsonObject> before, JsonObject defaultValue) {
        JsonObject params = defaultValue;
        if (before != null) {
            try {
                params = before.call(params);
            } catch (Exception e) {
                logger.error("Before handler got exception: {}", e);
                throw e;
            }
        }
        return params;
    }

    private SimpleResponse processResponseIfAfterHandlerExists(Closure<SimpleResponse> after, SimpleResponse defaultValue) {
        SimpleResponse result = defaultValue;
        if (after != null) {
            try {
                result = after.call(result);
            } catch (Exception e) {
                logger.error("After handler got exception: {}", e);
                throw e;
            }
        }
        return result;
    }

    private int finalStatusCode(List<Integer> statusCodes) {
        if (statusCodes.stream().allMatch(statusCode -> statusCode >= 200 && statusCode < 300)) {
            return 200;
        } else if (statusCodes.stream().allMatch(statusCode -> statusCode >= 400)) {
            return 500;
        } else {
            return 206;
        }
    }

    private void putResponseToCache(String requestURI, UpstreamURL upstreamURL
            , JsonObject token, JsonObject payload, int expires) {
        ResponseHolder.put(nameOfApiGateway, urlConfig.getUrl()
                , upstreamURL.getHost(), upstreamURL.getPort(), upstreamURL.getUrl()
                , requestURI, token, payload, expires);
    }

    private boolean isResponseCached(String requestURI
            , UpstreamURL upstreamURL, JsonObject token) {
        return upstreamURL.getExpires() > 0 &&
                ResponseHolder.containsCacheEntry(nameOfApiGateway, urlConfig.getUrl()
                        , upstreamURL.getHost(), upstreamURL.getPort()
                        , upstreamURL.getUrl(), requestURI, token);
    }

    private JsonObject getResponseFromCache(String requestURI
            , UpstreamURL upstreamURL, JsonObject token) {
        return ResponseHolder.get(nameOfApiGateway, urlConfig.getUrl()
                , upstreamURL.getHost(), upstreamURL.getPort()
                , upstreamURL.getUrl(), requestURI, token);
    }
}
