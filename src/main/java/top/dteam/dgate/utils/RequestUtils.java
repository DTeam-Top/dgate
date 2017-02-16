package top.dteam.dgate.utils;

import top.dteam.dgate.gateway.SimpleResponse;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public class RequestUtils {

    private HttpClient httpClient;

    public RequestUtils(Vertx vertx) {
        httpClient = vertx.createHttpClient();
    }

    public void get(String host, int port, String url, JsonObject data, Handler<SimpleResponse> handler) {
        request(HttpMethod.GET, host, port, url, data, handler);
    }

    public void post(String host, int port, String url, JsonObject data, Handler<SimpleResponse> handler) {
        request(HttpMethod.POST, host, port, url, data, handler);
    }

    public void delete(String host, int port, String url, JsonObject data, Handler<SimpleResponse> handler) {
        request(HttpMethod.DELETE, host, port, url, data, handler);
    }

    public void request(HttpMethod method, String host, int port, String url, JsonObject data, Handler<SimpleResponse> handler) {
        httpClient.request(method, port, host, url, defaultResponseHandler(handler))
                .setChunked(true).putHeader("content-type", "application/json").end(data.toString());
    }

    public void requestWithJwtToken(HttpMethod method, String host, int port, String url, JsonObject data, String token,
                                    Handler<SimpleResponse> handler) {
        httpClient.request(method, port, host, url, defaultResponseHandler(handler))
                .setChunked(true)
                .putHeader("content-type", "application/json")
                .putHeader("Authorization", String.format("Bearer %s", token))
                .end(data.toString());
    }

    private Handler<HttpClientResponse> defaultResponseHandler(Handler<SimpleResponse> handler) {
        return response -> {
            SimpleResponse simpleResponse = new SimpleResponse();
            simpleResponse.setStatusCode(response.statusCode());
            response.bodyHandler(totalBuffer -> {
                if (totalBuffer.length() > 0) {
                    simpleResponse.setPayload(totalBuffer.toJsonObject());
                }
                handler.handle(simpleResponse);
            });
        };
    }

}
