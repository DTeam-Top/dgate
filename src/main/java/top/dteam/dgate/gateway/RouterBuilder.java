package top.dteam.dgate.gateway;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dteam.dgate.config.ApiGatewayConfig;
import top.dteam.dgate.config.CorsConfig;
import top.dteam.dgate.config.LoginConfig;
import top.dteam.dgate.config.UrlConfig;
import top.dteam.dgate.handler.JWTTokenRefreshHandler;
import top.dteam.dgate.handler.RequestHandler;
import top.dteam.dgate.utils.JWTTokenRefresher;
import top.dteam.dgate.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        LoginConfig login = apiGatewayConfig.getLogin();
        JWTAuth auth = createAuthIfNeeded(vertx, router, login, urlConfigs);

        urlConfigs.forEach(urlConfig -> {
            if (login != null && urlConfig.getUrl().equals(login.login())) {
                router.route(urlConfig.getUrl()).handler(RequestHandler.create(vertx, urlConfig, auth)
                        .nameOfApiGateway(apiGatewayConfig.getName()));
            } else {
                router.route(urlConfig.getUrl()).handler(RequestHandler.create(vertx, urlConfig, null)
                        .nameOfApiGateway(apiGatewayConfig.getName()));
            }
        });
    }

    private static JWTAuth createAuthIfNeeded(Vertx vertx, Router router, LoginConfig login,
                                              List<UrlConfig> urlConfigs) {
        if (login != null) {

            // this handler MUST BE the first handler if login is enabled !!!
            createTokenFreshHandler(vertx, router, login);

            JWTAuth jwtAuth = Utils.createAuthProvider(vertx);
            JWTAuthHandler jwtAuthHandler = JWTAuthHandler.create(jwtAuth, login.login());
            if (login.only().isEmpty() && login.ignore().isEmpty()) {
                router.route().handler(jwtAuthHandler);
            } else if (login.ignore().isEmpty()) {
                login.only().forEach(url -> router.route(url).handler(jwtAuthHandler));
            } else if (login.only().isEmpty()) {
                List<String> allUrls = urlConfigs.stream().map(config -> config.getUrl()).collect(Collectors.toList());
                allUrls.removeAll(login.ignore());
                allUrls.remove(login.login());

                allUrls.forEach(url -> router.route(url).handler(jwtAuthHandler));
            }

            return jwtAuth;
        } else {
            return null;
        }
    }

    private static void createTokenFreshHandler(Vertx vertx, Router router, LoginConfig login) {
        JWTTokenRefresher jwtTokenRefresher = new JWTTokenRefresher(vertx);
        router.route(JWTTokenRefreshHandler.URL).handler(
                new JWTTokenRefreshHandler(jwtTokenRefresher, login.refreshLimit(), login.refreshExpire()));
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
