package top.dteam.dgate.handler;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import top.dteam.dgate.config.ProxyUrlConfig;
import top.dteam.dgate.utils.JWTTokenGenerator;

import java.util.HashMap;
import java.util.Map;


public class LoginHandler extends ProxyHandler {

    private JWTTokenGenerator tokenGenerator;

    public LoginHandler(Vertx vertx, ProxyUrlConfig urlConfig, JWTAuth jwtAuth) {
        super(vertx, urlConfig);
        this.tokenGenerator = new JWTTokenGenerator(jwtAuth);
    }

    @Override
    protected Map createAfterContext() {
        Map context = new HashMap<>();
        context.put("tokenGenerator", tokenGenerator);
        return context;
    }
}
