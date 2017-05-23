package top.dteam.dgate.utils;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWT;

public class JWTTokenRefresher {

    private JWT jwt;
    private JWTTokenGenerator tokenGenerator;
    private JsonObject payload;

    public JWTTokenRefresher(Vertx vertx) {
        tokenGenerator = new JWTTokenGenerator(Utils.createAuthProvider(vertx));
        jwt = Utils.createJWT(vertx);
    }

    public void setPayload(String payload) {
        this.payload = jwt.decode(payload);
    }

    public boolean lessThan(long refreshLimit) {
        return ((System.currentTimeMillis() / 1000) - payload.getLong("exp")) <= refreshLimit;
    }

    public String refresh(long refreshExpire) {
        payload.remove("exp");
        payload.remove("iat");
        payload.remove("nbf");
        payload.remove("aud");
        payload.remove("iss");
        return tokenGenerator.token(payload.getMap(), refreshExpire);
    }

}
