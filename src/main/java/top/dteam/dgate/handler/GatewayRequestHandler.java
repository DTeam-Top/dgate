package top.dteam.dgate.handler;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import top.dteam.dgate.config.UrlConfig;

public interface GatewayRequestHandler extends Handler<RoutingContext> {

    GatewayRequestHandler nameOfApiGateway(String nameOfApiGateway);

    static GatewayRequestHandler create(Vertx vertx, UrlConfig urlConfig, JWTAuth jwtAuth) {
        int requestHandlerType = urlConfig.requestHandlerType();
        if (requestHandlerType == UrlConfig.PROXY) {
            if (jwtAuth == null) {
                return new ProxyHandler(vertx, urlConfig);
            } else {
                return new LoginHandler(vertx, urlConfig, jwtAuth);
            }
        } else if (requestHandlerType == UrlConfig.MOCK) {
            return new MockHandler(vertx, urlConfig);
        } else if (requestHandlerType == UrlConfig.RELAY) {
            return new RelayHandler(vertx, urlConfig);
        } else {
            return null;
        }
    }

}
