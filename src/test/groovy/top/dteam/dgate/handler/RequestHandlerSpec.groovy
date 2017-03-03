package top.dteam.dgate.handler

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import spock.lang.Specification
import spock.lang.Unroll
import top.dteam.dgate.config.MockUrlConfig
import top.dteam.dgate.gateway.SimpleResponse
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils

class RequestHandlerSpec extends Specification {

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
    def "required could be a list: #url (#body)"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", 8081, url, new JsonObject(body)) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == statusCode

        where:
        body                             | statusCode | url
        [:]                              | 400        | "/required-list"
        [param1: 'test']                 | 400        | "/required-list"
        [param2: 'test']                 | 400        | "/required-list"
        [param1: 'test', param2: 'test'] | 200        | "/required-list"
        [:]                              | 400        | "/required-list"
        [:]                              | 400        | "/required-list?param1=test"
        [:]                              | 400        | "/required-list?param2=test"
        [:]                              | 200        | "/required-list?param1=test&&param2=test"
        [param2: 'test']                 | 200        | "/required-list?param1=test"
    }

    @Unroll
    def "required could be a map: #url (#body)"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, url, new JsonObject(body)) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == statusCode

        where:
        method   | body             | statusCode | url
        'get'    | [:]              | 400        | "/required-map"
        'get'    | [:]              | 200        | "/required-map?param1=test"
        'post'   | [:]              | 400        | "/required-map"
        'post'   | [param2: 'test'] | 200        | "/required-map"
        'delete' | [:]              | 400        | "/required-map"
        'delete' | [param3: 'test'] | 200        | "/required-map"

    }

    private HttpServer createGate() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8081)

        router.route("/required-list").handler(new MockHandler(vertx,
                new MockUrlConfig(required: ['param1', 'param2'], expected: [statusCode: 200, payload: [method: 'all']])))
        router.route("/required-map").handler(new MockHandler(vertx,
                new MockUrlConfig(required: [get: ['param1'], post: ['param2'], delete: ['param3']],
                        expected: [statusCode: 200, payload: [method: 'all']])))

        httpServer
    }

}
