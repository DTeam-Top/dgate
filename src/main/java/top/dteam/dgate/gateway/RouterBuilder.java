package top.dteam.dgate.gateway;

import top.dteam.dgate.config.ApiGatewayConfig;
import top.dteam.dgate.config.CorsConfig;
import top.dteam.dgate.config.UrlConfig;
import top.dteam.dgate.handler.LoginHandler;
import top.dteam.dgate.handler.RequestHandler;
import top.dteam.dgate.utils.Utils;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouterBuilder {

    private static final Logger logger = LoggerFactory.getLogger(RouterBuilder.class);

    public static Router builder(Vertx vertx, ApiGatewayConfig apiGatewayConfig) {
        Router router = Router.router(vertx);
        addCorsHandler(router, apiGatewayConfig);
        addBodyHandler(router);
        addRequestHandlers(vertx, router, apiGatewayConfig);
        addFailureHandler(router);
        return router;
    }

    private static void addCorsHandler(Router router, ApiGatewayConfig apiGatewayConfig) {
        CorsConfig corsConfig = apiGatewayConfig.getCors();
        if (corsConfig != null) {
            CorsHandler corsHandler = CorsHandler.create(corsConfig.getAllowedOriginPattern());

            if (corsConfig.getAllowedMethods() != null) {
                corsHandler.allowedMethods(corsConfig.getAllowedMethods());
            }

            if (corsConfig.getAllowCredentials() != null) {
                corsHandler.allowCredentials(corsConfig.getAllowCredentials());
            }

            if (corsConfig.getAllowedHeaders() != null) {
                corsHandler.allowedHeaders(corsConfig.getAllowedHeaders());
            }

            if (corsConfig.getExposedHeaders() != null) {
                corsHandler.exposedHeaders(corsConfig.getExposedHeaders());
            }

            if (corsConfig.getMaxAgeSeconds() != null) {
                corsHandler.maxAgeSeconds(corsConfig.getMaxAgeSeconds());
            }

            router.route().handler(corsHandler);
        }
    }

    private static void addBodyHandler(Router router) {
        router.route().handler(BodyHandler.create());
    }

    private static void addRequestHandlers(Vertx vertx, Router router, ApiGatewayConfig apiGatewayConfig) {
        List<UrlConfig> urlConfigs = apiGatewayConfig.getUrlConfigs();
        String login = apiGatewayConfig.getLogin();

        urlConfigs.forEach(urlConfig -> {
            if (login != null && urlConfig.getUrl().equals(login)) {
                addLoginHandler(vertx, router, login, urlConfig, apiGatewayConfig.getName());
            } else {
                router.route(urlConfig.getUrl()).handler(RequestHandler.create(vertx, urlConfig)
                        .nameOfApiGateway(apiGatewayConfig.getName()));
            }
        });
    }

    private static void addLoginHandler(Vertx vertx, Router router, String login, UrlConfig urlConfig,
                                        String nameOfApiGateway) {
        JWTAuth jwtAuth = Utils.createAuthProvider(vertx);
        router.route().handler(JWTAuthHandler.create(jwtAuth, login));
        if (urlConfig.requestHandlerType() == UrlConfig.PROXY) {
            router.route(login).handler(new LoginHandler(vertx, urlConfig,
                    new CircuitBreakerOptions().setMaxFailures(3).setTimeout(5000).setResetTimeout(10000),
                    jwtAuth).nameOfApiGateway(nameOfApiGateway));
        } else {
            router.route(login).handler(RequestHandler.create(vertx, urlConfig)
                    .nameOfApiGateway(nameOfApiGateway));
        }
    }

    private static void addFailureHandler(Router router) {
        router.route().failureHandler(routingContext -> {
            if (routingContext.response().ended()) {
                return;
            }

            int statusCode = routingContext.statusCode() == -1 ? 500 : routingContext.statusCode();

            logger.error("Got [{}] during processing [{}], status code: {}", routingContext.response().getStatusMessage(),
                    routingContext.request().absoluteURI(), statusCode);

            Map<String, String> payload = new HashMap<>();
            payload.put("error", routingContext.response().getStatusMessage());
            Utils.fireJsonResponse(routingContext.response(), statusCode, payload);
        });
    }
}
