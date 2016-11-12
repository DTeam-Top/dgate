package top.dteam.dgate.handler

import top.dteam.dgate.config.UrlConfig
import top.dteam.dgate.utils.Utils
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject

class MockHandler extends RequestHandler {

    private Map expectedResponse

    MockHandler(Vertx vertx, UrlConfig urlConfig) {
        super(vertx, urlConfig)
        this.expectedResponse = urlConfig.expected
    }

    @Override
    protected void processRequestBody(HttpServerRequest request, HttpServerResponse response, JsonObject body) {
        if (expectedResponse.statusCode && expectedResponse.payload) {
            Utils.fireJsonResponse(response, transformIfNeeded(expectedResponse.statusCode),
                    transformIfNeeded(expectedResponse.payload))
        } else {
            String key = request.method().toString().toLowerCase()
            Utils.fireJsonResponse(response, transformIfNeeded(expectedResponse[key].statusCode),
                    transformIfNeeded(expectedResponse[key].payload))
        }
    }

    private def transformIfNeeded(def initValue) {
        (initValue instanceof Closure) ? initValue() : initValue
    }

}
