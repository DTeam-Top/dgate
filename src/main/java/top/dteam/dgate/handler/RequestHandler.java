package top.dteam.dgate.handler;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dteam.dgate.config.InvalidConfiguriationException;
import top.dteam.dgate.config.UrlConfig;
import top.dteam.dgate.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class RequestHandler implements GatewayRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    protected Vertx vertx;
    protected UrlConfig urlConfig;
    protected String nameOfApiGateway;

    protected RequestHandler(Vertx vertx, UrlConfig urlConfig) {
        this.vertx = vertx;
        this.urlConfig = urlConfig;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        verifyMethodsAllowed(routingContext);

        if (routingContext.request().isEnded()) {
            processRequest(routingContext, routingContext.getBody());
        } else {
            routingContext.request().bodyHandler(totalBuffer -> processRequest(routingContext, totalBuffer));
        }
    }

    private Object requiredParams() {
        return urlConfig.getRequired();
    }

    private List<HttpMethod> allowedMethods() {
        return urlConfig.getMethods();
    }

    public GatewayRequestHandler nameOfApiGateway(String nameOfApiGateway) {
        this.nameOfApiGateway = nameOfApiGateway;
        return this;
    }

    protected abstract void processRequestBody(HttpServerRequest request, HttpServerResponse response, JsonObject body);

    private void verifyMethodsAllowed(RoutingContext routingContext) {
        if (allowedMethods() != null && !allowedMethods().isEmpty()) {
            if (allowedMethods().stream().anyMatch(method -> routingContext.request().method() == method)) {
                return;
            } else {
                HashMap error = new HashMap();
                error.put("error", "Unsupported HTTP Method.");
                Utils.fireJsonResponse(routingContext.response(), 400, error);
                throw new UnsupportedOperationException("Unsupported HTTP Method.");
            }
        }
    }

    private void verifyRequiredExists(RoutingContext routingContext, JsonObject body) {
        if (requiredParams() == null) {
            return;
        }

        List<String> params;
        if (requiredParams() instanceof List) {
            params = (List) requiredParams();
        } else if (requiredParams() instanceof Map) {
            params = (List) ((Map) requiredParams()).get(routingContext.request().method().toString().toLowerCase());
        } else {
            throw new InvalidConfiguriationException("required must be List or Map:" + requiredParams().getClass().getName());
        }

        if (params != null && !params.isEmpty()) {
            if (params.stream().anyMatch(param -> !body.containsKey(param))) {
                HashMap error = new HashMap();
                error.put("error", "required params not in request.");
                Utils.fireJsonResponse(routingContext.response(), 400, error);
                throw new IllegalArgumentException("required params not in request");
            }
        }
    }

    private JsonObject getBodyFromBuffer(Buffer buffer) {
        if (buffer.toString().trim().length() == 0) {
            return new JsonObject();
        } else {
            return buffer.toJsonObject();
        }
    }

    private void putJwtTokenInBody(JsonObject body, RoutingContext routingContext) {
        if (routingContext.user() != null) {
            JsonObject token = routingContext.user().principal();
            body.put("token", token);
        } else if (routingContext.get("token") != null) {
            body.put("token", (JsonObject) routingContext.get("token"));
        }
    }

    private void putNameOfApiGatewayInBody(JsonObject body) {
        if (nameOfApiGateway != null) {
            body.put("nameOfApiGateway", nameOfApiGateway);
        }
    }

    private void mergeRequestParams(JsonObject body, MultiMap params) {
        if (params == null) {
            return;
        }

        params.forEach(entry -> body.put(entry.getKey(), entry.getValue()));

        logger.debug("Merged paramaters {}: ", body);
    }

    private void processRequest(RoutingContext routingContext, Buffer buffer) {
        JsonObject body = getBodyFromBuffer(buffer);
        mergeRequestParams(body, routingContext.request().params());
        verifyRequiredExists(routingContext, body);
        putJwtTokenInBody(body, routingContext);
        putNameOfApiGatewayInBody(body);

        processRequestBody(routingContext.request(), routingContext.response(), body);
    }
}
