package top.dteam.dgate.handler

import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import spock.lang.Specification
import spock.lang.Unroll
import top.dteam.dgate.config.ProxyUrlConfig
import top.dteam.dgate.config.UpstreamURL
import top.dteam.dgate.gateway.SimpleResponse
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils
import top.dteam.dgate.utils.Utils

class ForwardRequestSpec extends Specification {

    private static final long OP_TIMEOUT = 1800
    private static final long RESET_TIMEOUT = 4000

    Vertx vertx
    HttpServer gate
    HttpServer dest
    RequestUtils requestUtils

    void setup() {
        vertx = Vertx.vertx()
        gate = createGate()
        dest = createDest()
        requestUtils = new RequestUtils(vertx)
    }

    void cleanup() {
        gate.close()
        dest.close()
        vertx.close()
    }

    @Unroll
    def "#method should be forwarded correctly"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, "/withoutHandler", params) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.toString() == new JsonObject([method: httpMethod, params: params]).toString()

        where:
        method   | params                             | httpMethod
        "get"    | new JsonObject([method: "get"])    | HttpMethod.GET
        "post"   | new JsonObject([method: "post"])   | HttpMethod.POST
        "delete" | new JsonObject([method: "delete"]) | HttpMethod.DELETE
    }

    def "should get 400【Bad Request】for unsupported method"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.request(HttpMethod.PUT, "localhost", 8081, "/withoutHandler", new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 400
    }

    def "should get 500 for 'operation timeout'"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.post("localhost", 8081, "/timeout", new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, OP_TIMEOUT + 500)

        then:
        result.statusCode == 500
        result.payload.map.error == "operation timeout"
    }

    def "should return immediately if Circuit Breaker is opened"() {
        setup:
        SimpleResponse result
        requestsMakingCBOpen()

        when:
        sleep(2000)
        requestUtils.post("localhost", 8081, "/unknown", new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, OP_TIMEOUT - 500)

        then:
        result.statusCode == 500
        result.payload.map.error == "open circuit"
    }

    @Unroll
    def "before handler and after handler in UpstreamURL should work. (#method)"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, "/withHandler", params) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.toString() == new JsonObject([method      : httpMethod,
                                                     params      : params.mergeIn(new JsonObject([addedByBefore: 'addedByBefore'])),
                                                     addedByAfter: 'addedByAfter']).toString()

        where:
        method   | params                             | httpMethod
        "get"    | new JsonObject([method: "get"])    | HttpMethod.GET
        "post"   | new JsonObject([method: "post"])   | HttpMethod.POST
        "delete" | new JsonObject([method: "delete"]) | HttpMethod.DELETE
    }

    @Unroll
    def "url template in UpstreamURL should work: #url(#params)"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, url, params) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.toString() == new JsonObject([method: httpMethod,
                                                     uri   : uri]).toString()

        where:
        method   | url                           | params                       | httpMethod        | uri
        "get"    | '/url-template?x=1&&y=2&&z=3' | new JsonObject([:])          | HttpMethod.GET    | '/url-template/1/2/3'
        "get"    | '/url-template?x=1&&y=2'      | new JsonObject([z: 3])       | HttpMethod.GET    | '/url-template/1/2/3'
        "post"   | '/url-template'               | new JsonObject([x: 1, y: 2]) | HttpMethod.POST   | '/url-template/1/2'
        "delete" | '/url-template'               | new JsonObject([x: 1])       | HttpMethod.DELETE | '/url-template/1'
    }

    @Unroll
    def "should get 500 if pathParams required by upstream not exist: #url(#params)"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", 8081, '/url-template', new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 500
    }

    def "should support pathParams"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", 8081, '/path-params/1', new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.toString() == new JsonObject([method: HttpMethod.GET, params: [id: '1']]).toString()
    }

    private HttpServer createGate() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8081)

        router.route("/withoutHandler").handler(new ProxyHandler(vertx,
                new ProxyUrlConfig(upstreamURLs: [new UpstreamURL(host: "localhost", port: 8082, url: "/normal",
                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))],
                        methods: [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE])))
        router.route("/timeout").handler(new ProxyHandler(vertx,
                new ProxyUrlConfig(upstreamURLs: [new UpstreamURL(host: "localhost", port: 8082, url: "/timeout",
                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))],
                        methods: [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE])))
        router.route("/withHandler").handler(new ProxyHandler(vertx,
                new ProxyUrlConfig(upstreamURLs: [
                        new UpstreamURL(host: "localhost", port: 8082, url: "/normal",
                                before: { jsonObject ->
                                    jsonObject.put('addedByBefore', 'addedByBefore')
                                    jsonObject
                                },
                                after: { simpleResponse ->
                                    simpleResponse.payload.put('addedByAfter', 'addedByAfter')
                                    simpleResponse
                                },
                                circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                        .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))],
                        methods: [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE])))
        router.route("/unknown").handler(new ProxyHandler(vertx,
                new ProxyUrlConfig(upstreamURLs: [new UpstreamURL(host: "localhost", port: 8082, url: "/unknown",
                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))],
                        methods: [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE])))
        router.route("/url-template").handler(new ProxyHandler(vertx,
                new ProxyUrlConfig(upstreamURLs: [new UpstreamURL(host: "localhost", port: 8082, url: "/url-template/:x/:y?/:z?",
                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))],
                        methods: [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE])))
        router.route("/path-params/:id").handler(new ProxyHandler(vertx,
                new ProxyUrlConfig(upstreamURLs: [new UpstreamURL(host: "localhost", port: 8082, url: "/normal",
                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))])))
        httpServer
    }

    private HttpServer createDest() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8082)

        router.route("/normal").handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 200,
                        [method: routingContext.request().method(),
                         params: totalBuffer.toJsonObject()])
            }
        }
        router.route("/timeout").handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                sleep(OP_TIMEOUT + 200)
                Utils.fireJsonResponse(routingContext.response(), 200,
                        [method: routingContext.request().method(),
                         params: totalBuffer.toJsonObject()])
            }
        }
        router.route().pathRegex("/url-template/.*").handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 200,
                        [method: routingContext.request().method(),
                         uri   : routingContext.request().uri()])
            }
        }

        httpServer
    }

    private void requestsMakingCBOpen() {
        requestUtils.post("localhost", 8081, "/unknown", new JsonObject()) { simpleResponse -> }
        requestUtils.post("localhost", 8081, "/unknown", new JsonObject()) { simpleResponse -> }
        requestUtils.post("localhost", 8081, "/unknown", new JsonObject()) { simpleResponse -> }
    }
}
