package top.dteam.dgate.handler

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import spock.lang.Specification
import spock.lang.Unroll
import top.dteam.dgate.config.UrlConfig
import top.dteam.dgate.gateway.SimpleResponse
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils

class MockHandlerSpec extends Specification {

    Vertx vertx
    HttpServer gate
    RequestUtils requestUtils

    void setup() {
        vertx = Vertx.vertx()
        gate = createGate()
        requestUtils = new RequestUtils(vertx)
    }

    void cleanup() {
        gate.close()
        vertx.close()
    }

    @Unroll
    def "若不指定具体HTTP Method则Mock的内容适合所有HTTP Method请求: #method"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, "/mock", new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.toJsonObject() == new JsonObject([statusCode: 200, payload: [method: 'all']])

        where:
        method << ['get', 'post', 'delete']
    }

    @Unroll
    def "应该返回对应HTTP Method的Mock: #method"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, "/mock-methods", new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.toJsonObject() == new JsonObject([statusCode: 200, payload: [method: method]])

        where:
        method << ['get', 'post', 'delete']
    }

    private HttpServer createGate() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8081)

        router.route("/mock").handler(
                new MockHandler(vertx,
                        new UrlConfig(required: null, expected: [statusCode: 200, payload: [method: 'all']])))
        router.route("/mock-methods").handler(
                new MockHandler(vertx,
                        new UrlConfig(required: null,
                                expected: [get   : [statusCode: { 200 }, payload: [method: 'get']],
                                           post  : [statusCode: 200, payload: [method: 'post']],
                                           delete: [statusCode: 200, payload: { [method: 'delete'] }]])))

        httpServer
    }

}
