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
import top.dteam.dgate.config.UrlConfig
import top.dteam.dgate.gateway.SimpleResponse
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils
import top.dteam.dgate.utils.Utils

class CompositeRequestSpec extends Specification {

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
    def "#method should work"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, "/allSuccess", params) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.toString() == new JsonObject([method1: httpMethod, params1: params,
                                                     method2: httpMethod, params2: params]).toString()

        where:
        method   | params                             | httpMethod
        "get"    | new JsonObject([method: "get"])    | HttpMethod.GET
        "post"   | new JsonObject([method: "post"])   | HttpMethod.POST
        "delete" | new JsonObject([method: "delete"]) | HttpMethod.DELETE
    }

    @Unroll
    def "[#url] should expect status code: #statusCode"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, url, new JsonObject([method: method])) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == statusCode

        where:
        method   | url               | statusCode
        "get"    | '/allSuccess'     | 200
        "post"   | '/allFailure'     | 500
        "delete" | '/partialSuccess' | 206
    }

    @Unroll
    def "could set context of before and after closures for [#method]"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils."$method"("localhost", 8081, "/checkHandlerContext", params) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.toString() == new JsonObject([method1: httpMethod, params1: params.mergeIn(new JsonObject([before: "before"])),
                                                     method2: httpMethod, params2: params, after: "after"]).toString()

        where:
        method   | params                             | httpMethod
        "get"    | new JsonObject([method: "get"])    | HttpMethod.GET
        "post"   | new JsonObject([method: "post"])   | HttpMethod.POST
        "delete" | new JsonObject([method: "delete"]) | HttpMethod.DELETE
    }


    private HttpServer createGate() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8081)

        router.route("/allSuccess").handler(new ProxyHandler(vertx,
                new ProxyUrlConfig(
                        upstreamURLs: Arrays.asList(
                                new UpstreamURL(host: "localhost", port: 8082, url: "/success1",
                                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT)),
                                new UpstreamURL(host: "localhost", port: 8082, url: "/success2",
                                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))
                        )
                )))
        router.route("/allFailure").handler(new ProxyHandler(vertx,
                new ProxyUrlConfig(
                        upstreamURLs: Arrays.asList(
                                new UpstreamURL(host: "localhost", port: 8082, url: "/failure1",
                                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT)),
                                new UpstreamURL(host: "localhost", port: 8082, url: "/failure2",
                                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))
                        )
                )))
        router.route("/partialSuccess").handler(new ProxyHandler(vertx,
                new ProxyUrlConfig(
                        upstreamURLs: Arrays.asList(
                                new UpstreamURL(host: "localhost", port: 8082, url: "/failure1",
                                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT)),
                                new UpstreamURL(host: "localhost", port: 8082, url: "/success2",
                                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))
                        )
                )))
        router.route("/checkHandlerContext").handler(new SubProxyHandler(vertx,
                new ProxyUrlConfig(
                        upstreamURLs: Arrays.asList(
                                new UpstreamURL(host: "localhost", port: 8082, url: "/success1", before: { params ->
                                    params.put("before", paramForBefore)
                                    params
                                },
                                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT)),
                                new UpstreamURL(host: "localhost", port: 8082, url: "/success2", after: { simpleResponse ->
                                    simpleResponse.payload.put("after", paramForAfter)
                                    simpleResponse
                                },
                                        circuitBreaker: new CircuitBreakerOptions().setMaxFailures(3)
                                                .setTimeout(OP_TIMEOUT).setResetTimeout(RESET_TIMEOUT))
                        )
                )))

        httpServer
    }

    private HttpServer createDest() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8082)

        router.route("/success1").handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 200,
                        [method1: routingContext.request().method(),
                         params1: totalBuffer.toJsonObject()])
            }
        }

        router.route("/success2").handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 200,
                        [method2: routingContext.request().method(),
                         params2: totalBuffer.toJsonObject()])
            }
        }

        router.route("/failure1").handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 500,
                        [method1: routingContext.request().method(),
                         params1: totalBuffer.toJsonObject()])
            }
        }

        router.route("/failure2").handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 500,
                        [method2: routingContext.request().method(),
                         params2: totalBuffer.toJsonObject()])
            }
        }



        httpServer
    }

    class SubProxyHandler extends ProxyHandler {

        SubProxyHandler(Vertx vertx, UrlConfig urlConfig) {
            super(vertx, urlConfig)
        }

        @Override
        protected Map createBeforeContext() {
            [paramForBefore: "before"]
        }

        @Override
        protected Map createAfterContext() {
            [paramForAfter: "after"]
        }
    }

}
