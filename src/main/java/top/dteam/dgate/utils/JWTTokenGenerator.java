package top.dteam.dgate.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.jwt.JWTOptions;

import java.util.Map;

public class JWTTokenGenerator {

    private JWTAuth jwtAuth;

    public JWTTokenGenerator(JWTAuth jwtAuth) {
        this.jwtAuth = jwtAuth;
    }

    public String token(Map<String, Object> payload, int expiration) {
        JWTOptions options = new JWTOptions();
        options.setExpiresInSeconds(expiration);
        return jwtAuth.generateToken(new JsonObject(payload), options);
    }

}
