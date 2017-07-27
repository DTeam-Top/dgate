package top.dteam.dgate.handler

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import spock.lang.Specification
import spock.lang.Unroll
import top.dteam.dgate.config.ApiGatewayRepository
import top.dteam.dgate.gateway.ApiGateway
import top.dteam.dgate.gateway.SimpleResponse
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils
import top.dteam.dgate.utils.Utils

class CircuitBreakerSpec extends Specification {

    private static final long DEFAULT_OP_TIMEOUT = 5000

    String config = """
        apiGateway1 {
            port = 7000
            urls {
                "/test" {
                    upstreamURLs = [
                        [ host: 'localhost', port: 8080, url: '/test-5s']
                    ]
                }
                "/relay" {
                    relayTo {
                        host = 'localhost'
                        port = 8080
                    }
                }
            }
        }
        apiGateway2 {
            port = 7001
            circuitBreaker {
                maxFailures = 2
                timeout = 2000
                resetTimeout = 5000
            }
            urls {
                "/test" {
                    upstreamURLs = [
                        [host: 'localhost', port: 8080, url: '/test-2s']
                    ]
                }
                "/relay" {
                    relayTo {
                        host = 'localhost'
                        port = 8080
                    }
                }
            }
        }
        apiGateway3 {
            port = 7002
            circuitBreaker {
                maxFailures = 2
                timeout = 5000
                resetTimeout = 2000
            }
            urls {
                "/test" {
                    upstreamURLs = [
                        [host: 'localhost', port: 8080, url: '/test-3s',
                         circuitBreaker: [maxFailures: 2, timeout: 3000, resetTimeout: 3000]]
                    ]
                }
                "/relay" {
                    relayTo {
                        host = 'localhost'
                        port = 8080
                        circuitBreaker = [maxFailures: 2, timeout: 3000, resetTimeout: 3000]
                    }
                }
            }
        }
    """

    Vertx vertx
    HttpServer dest
    RequestUtils requestUtils

    void setup() {
        vertx = Vertx.vertx()
        deployGate()
        dest = createDest()
        requestUtils = new RequestUtils(vertx)
    }

    void cleanup() {
        dest.close()
        vertx.close()
    }

    @Unroll
    def "CircuitBreak Default Options should work"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.post("localhost", 7000, url, new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, DEFAULT_OP_TIMEOUT + 500)

        then:
        result.statusCode == 500
        result.payload.map.error == "operation timeout"

        where:
        url << ['/test', '/relay']
    }

    @Unroll
    def "Global CircuitBreak Options should override the default Circuit Break Options"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.post("localhost", 7001, url, new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 2000 + 500)

        then:
        result.statusCode == 500
        result.payload.map.error == "operation timeout"

        where:
        url << ['/test', '/relay']
    }

    @Unroll
    def "Circuit Break Options for specific URL should be used first"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.post("localhost", 7002, url, new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 3000 + 500)

        then:
        result.statusCode == 500
        result.payload.map.error == "operation timeout"

        where:
        url << ['/test', '/relay']
    }

    private HttpServer createDest() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8080)

        router.route().handler { routingContext ->
            sleep(DEFAULT_OP_TIMEOUT + 200)
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 200, [test: true])
            }
        }

        httpServer
    }

    private void deployGate() {
        ApiGatewayRepository.respository.clear()
        ApiGatewayRepository.build(config)

        vertx.deployVerticle(new ApiGateway(ApiGatewayRepository.respository[0]))
        vertx.deployVerticle(new ApiGateway(ApiGatewayRepository.respository[1]))
        vertx.deployVerticle(new ApiGateway(ApiGatewayRepository.respository[2]))
    }

}
