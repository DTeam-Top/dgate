package top.dteam.dgate.utils;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dteam.dgate.gateway.SimpleResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

public class RequestUtils {

    public static final String JWT_HEADER = "dgate-jwt-token";
    public static final String API_GATEWAY_NAME_HEADER = "dgate-gateway";

    private static final Logger logger = LoggerFactory.getLogger(RequestUtils.class);

    private Vertx vertx;
    private HttpClient httpClient;

    public RequestUtils(Vertx vertx) {
        this.vertx = vertx;
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
        HttpClientRequest request = httpClient.request(method, port, host, url, defaultResponseHandler(handler))
                .setChunked(true)
                .putHeader("content-type", "application/json");
        if (data.getJsonObject("token") != null) {
            request.putHeader(JWT_HEADER, Base64.getEncoder().encodeToString(data.getJsonObject("token").toString().getBytes()));
        }

        if (data.getString("nameOfApiGateway") != null) {
            request.putHeader(API_GATEWAY_NAME_HEADER, Base64.getEncoder().encodeToString(data.getString("nameOfApiGateway").toString().getBytes()));
        }

        request.end(data.toString());
    }

    public void requestWithJwtToken(HttpMethod method, String host, int port, String url, JsonObject data, String token,
                                    Handler<SimpleResponse> handler) {
        httpClient.request(method, port, host, url, defaultResponseHandler(handler))
                .setChunked(true)
                .putHeader("content-type", "application/json")
                .putHeader("Authorization", String.format("Bearer %s", token))
                .end(data.toString());
    }

    public HttpClientRequest relay(HttpMethod method, String host, int port, String url, Handler<SimpleResponse> handler) {
        return httpClient.request(method, port, host, url, defaultResponseHandler(handler));
    }

    public void upload(String file, String host, int port, String url, Handler<SimpleResponse> handler) {
        HttpClientRequest request = httpClient.post(port, host, url, defaultResponseHandler(handler));
        try {
            Buffer bodyBuffer = getBody(file, "file", "MyBoundary");
            request.putHeader("Content-Type", "multipart/form-data;boundary=MyBoundary")
                    .putHeader("Content-Length", String.valueOf(bodyBuffer.length()));
            request.end(bodyBuffer);
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void form(JsonObject form, String host, int port, String url, Handler<SimpleResponse> handler) {
        HttpClientRequest request = httpClient.post(port, host, url, defaultResponseHandler(handler));
        Buffer bodyBuffer = formData(form);
        request.putHeader("Content-Type", "application/x-www-form-urlencoded")
                .putHeader("Content-Length", String.valueOf(bodyBuffer.length()));
        request.end(bodyBuffer);
    }

    public String getJwtHeader(HttpServerRequest request) {
        if (request.headers().contains(RequestUtils.JWT_HEADER)) {
            return new String(Base64.getDecoder().decode(request.getHeader(RequestUtils.JWT_HEADER)));
        } else {
            return "";
        }
    }

    public String getAPIGatewayNameHeader(HttpServerRequest request) {
        if (request.headers().contains(RequestUtils.API_GATEWAY_NAME_HEADER)) {
            return new String(Base64.getDecoder().decode(request.getHeader(RequestUtils.API_GATEWAY_NAME_HEADER)));
        } else {
            return "";
        }
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

    private Buffer getBody(String filename, String name, String boundary) throws IOException {
        Buffer buffer = Buffer.buffer();
        buffer.appendString(String.format("--%s\r\n", boundary));
        buffer.appendString(String.format("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n", name, filename));
        buffer.appendString("Content-Type: application/octet-stream\r\n");
        buffer.appendString(String.format("Content-Length: %d\r\n", Files.size(Paths.get(filename))));
        buffer.appendString("Content-Transfer-Encoding: binary\r\n");
        buffer.appendString("\r\n");
        try {
            buffer.appendBytes(Files.readAllBytes(Paths.get(filename)));
            buffer.appendString("\r\n");
        } catch (IOException e) {
            e.printStackTrace();

        }
        buffer.appendString(String.format("--%s--\r\n", boundary));
        return buffer;
    }

    private Buffer formData(JsonObject payload) {
        if (payload.isEmpty()) {
            return Buffer.buffer();
        }

        Buffer buffer = Buffer.buffer();
        if (payload.size() == 1) {
            Map.Entry<String, Object> entry = payload.iterator().next();
            try {
                buffer.appendString(entry.getKey()).appendString("=")
                        .appendString(URLEncoder.encode(entry.getValue().toString(), "utf-8"));
            } catch (UnsupportedEncodingException e) {
                logger.error(e.getMessage());
            }
        } else {
            payload.forEach(entry -> {
                try {
                    buffer.appendString(entry.getKey()).appendString("=")
                            .appendString(URLEncoder.encode(entry.getValue().toString(), "utf-8"))
                            .appendString("&");
                } catch (UnsupportedEncodingException e) {
                    logger.error(e.getMessage());
                }
            });
        }
        return buffer;
    }

}
