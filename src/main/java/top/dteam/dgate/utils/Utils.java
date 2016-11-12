package top.dteam.dgate.utils;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;

import java.util.Map;

public class Utils {

    public static void fireSingleMessageResponse(HttpServerResponse response, int statusCode) {
        response.setStatusCode(statusCode).end();
    }

    public static void fireSingleMessageResponse(HttpServerResponse response, int statusCode, String message) {
        response.setStatusCode(statusCode).end(message);
    }

    public static void fireJsonResponse(HttpServerResponse response, int statusCode, Map payload) {
        response.setStatusCode(statusCode);
        JsonObject jsonObject = new JsonObject(payload);
        response.putHeader("content-type", "text/json; charset=utf-8").end(jsonObject.toString());
    }

    public static JWTAuth createAuthProvider(Vertx vertx) {
        JsonObject config = new JsonObject().put("keyStore", new JsonObject()
                .put("path", "dgate.jceks")
                .put("type", "jceks")
                .put("password", "dcloud"));

        return JWTAuth.create(vertx, config);
    }

}
