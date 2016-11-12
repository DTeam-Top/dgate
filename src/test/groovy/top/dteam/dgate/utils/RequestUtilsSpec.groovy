package top.dteam.dgate.utils

import top.dteam.dgate.gateway.SimpleResponse
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import spock.lang.Specification
import spock.lang.Unroll

class RequestUtilsSpec extends Specification {

    Vertx vertx
    HttpServer httpServer
    Router router
    RequestUtils requestUtils

    void setup() {
        vertx = Vertx.vertx()
        httpServer = vertx.createHttpServer()
        router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8081)
        requestUtils = new RequestUtils(vertx)
    }

    void cleanup() {
        httpServer.close()
        vertx.close()
    }

    @Unroll
    def "#method方法应该可以正常工作【hasBody=#hasBody】"() {
        setup:
        SimpleResponse result
        router.route("/test").handler(createHandler(hasBody))

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, "/test", params) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        if (hasBody) {
            result.payload.toString() == new JsonObject([method: httpMethod, params: params]).toString()
        } else {
            !result.payload
        }

        cleanup:
        router.clear()

        where:
        method   | params                             | httpMethod        | hasBody
        "get"    | new JsonObject([method: "get"])    | HttpMethod.GET    | true
        "get"    | new JsonObject([method: "get"])    | HttpMethod.GET    | false
        "post"   | new JsonObject([method: "post"])   | HttpMethod.POST   | true
        "post"   | new JsonObject([method: "post"])   | HttpMethod.POST   | false
        "delete" | new JsonObject([method: "delete"]) | HttpMethod.DELETE | true
        "delete" | new JsonObject([method: "delete"]) | HttpMethod.DELETE | false
    }

    private Closure createHandler(boolean hasBody) {
        { routingContext ->
            routingContext.request().bodyHandler({ totalBuffer ->
                if (hasBody) {
                    Utils.fireJsonResponse(routingContext.response(), 200,
                            [method: routingContext.request().method(),
                             params: totalBuffer.toJsonObject()])
                } else {
                    Utils.fireSingleMessageResponse(routingContext.response(), 200)
                }
            })
        }
    }

}
