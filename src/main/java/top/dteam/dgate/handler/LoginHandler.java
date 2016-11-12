package top.dteam.dgate.handler;

import top.dteam.dgate.config.UrlConfig;
import top.dteam.dgate.utils.JWTTokenGenerator;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;

import java.util.HashMap;
import java.util.Map;


public class LoginHandler extends ProxyHandler {

    private JWTTokenGenerator tokenGenerator;

    public LoginHandler(Vertx vertx, UrlConfig urlConfig, CircuitBreakerOptions circuitBreakerOptions, JWTAuth jwtAuth) {
        super(vertx, urlConfig, circuitBreakerOptions);
        this.tokenGenerator = new JWTTokenGenerator(jwtAuth);
    }

    @Override
    protected Map createAfterContext() {
        Map context = new HashMap<>();
        context.put("tokenGenerator", tokenGenerator);
        return context;
    }
}
