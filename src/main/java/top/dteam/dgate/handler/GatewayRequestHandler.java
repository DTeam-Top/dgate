package top.dteam.dgate.handler;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import top.dteam.dgate.config.*;

public interface GatewayRequestHandler extends Handler<RoutingContext> {

    GatewayRequestHandler nameOfApiGateway(String nameOfApiGateway);

    static GatewayRequestHandler create(Vertx vertx, UrlConfig urlConfig, JWTAuth jwtAuth) {
        if (ProxyUrlConfig.class == urlConfig.getClass()) {
            if (jwtAuth == null) {
                return new ProxyHandler(vertx, (ProxyUrlConfig) urlConfig);
            } else {
                return new LoginHandler(vertx, (ProxyUrlConfig) urlConfig, jwtAuth);
            }
        } else if (MockUrlConfig.class == urlConfig.getClass()) {
            return new MockHandler(vertx, (MockUrlConfig) urlConfig);
        } else if (RelayUrlConfig.class == urlConfig.getClass()) {
            return new RelayHandler(vertx, (RelayUrlConfig) urlConfig);
        } else {
            throw new InvalidConfiguriationException(String.format("Unknown URL Config Type: %s", urlConfig.getClass()));
        }
    }

}
