package top.dteam.dgate.handler

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import top.dteam.dgate.config.ApiGatewayRepository
import top.dteam.dgate.config.MockUrlConfig
import top.dteam.dgate.gateway.ApiGateway
import top.dteam.dgate.gateway.SimpleResponse
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils
import top.dteam.dgate.utils.Utils
import top.dteam.dgate.utils.cache.CacheLocator

import java.time.ZonedDateTime

class RelayHandlerWithCacheSpec extends Specification {
    private static final Logger logger = LoggerFactory.getLogger(RelayHandlerWithCacheSpec.class)

    private static final String CONFIG = '''
        apiGateway1 {
            port = 9010
            urls {
                "/random1" {
                    expires = 7000
                    relayTo {
                        host = 'localhost'
                        port = 9011
                    }
                }
            }
        }

        apiGateway2 {
            port = 9012
            login = "/login"
            urls {
                "/login" {
                    required = ["sub", "password"]
                    methods = ['GET', 'POST']
                    upstreamURLs = [
                        [host: 'localhost', port: 9013, url: '/login',
                        after: { simpleResponse ->
                                Map payload = [
                                    sub: simpleResponse.payload.getString("sub"),
                                    password: simpleResponse.payload.getString("password")
                                ]
                                simpleResponse.payload.put('token', delegate.tokenGenerator.token(payload, 50))
                                simpleResponse
                        }]
                    ]
                }
                "/forward" {
                    expires = 7000
                    relayTo {
                        host = 'localhost'
                        port = 9013
                    }
                }
            }
        }
    '''

    private static Vertx vertx
    private static RequestUtils requestUtils
    private static HttpServer mockServer
    private static HttpServer destServer

    void setupSpec() {
        Vertx.clusteredVertx(new VertxOptions(), { res ->
            if (res.succeeded()) {
                vertx = res.result()
                requestUtils = new RequestUtils(vertx)
                mockServer = createMock()
                destServer = createDest()
                ApiGatewayRepository.respository.clear()
                ApiGatewayRepository.build(CONFIG)
                ApiGatewayRepository.respository.each {
                    vertx.deployVerticle(new ApiGateway(it))
                }

                CacheLocator.init(vertx)
            } else {
                throw new RuntimeException("Starting up cluster vertx failed.")
            }
        })

        TestUtils.waitResult(mockServer, 10000)
        TestUtils.waitResult(destServer, 10000)
    }

    void cleanupSpec() {
        mockServer.close()
        destServer.close()
        CacheLocator.close()
        vertx.close()
    }

    def "expires should be working"() {
        setup:
        SimpleResponse result
        ZonedDateTime now = ZonedDateTime.now()

        when:
        sleep(100)
        requestUtils.get("localhost", 9010, "/random1", new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 2000)

        then:
        1.upto(8) {
            SimpleResponse result1
            requestUtils.get("localhost", 9010, "/random1"
                    , new JsonObject()) { simpleResponse ->
                result1 = simpleResponse
            }
            TestUtils.waitResult(result1, 2000)

            logger.debug("result = ${result.toJsonObject()}")
            logger.debug("result1 = ${result1.toJsonObject()}")

            if (ZonedDateTime.now() < now.plusSeconds(7)) {
                result == result1
            } else {
                result != result1
            }

            sleep(1000)
        }
    }

    def "different token should get different caches"() {
        setup:
        SimpleResponse result1
        SimpleResponse result2
        String token1
        String token2
        ZonedDateTime now = ZonedDateTime.now()

        when:
        requestUtils.get('localhost', 9012, '/login?sub=a&password=a'
                , new JsonObject()) { simpleResponse ->
            token1 = simpleResponse.payload.getString("token")

            requestUtils.requestWithJwtToken(HttpMethod.GET, 'localhost'
                    , 9012, '/forward', new JsonObject(), token1) { response ->
                result1 = response
            }
        }
        requestUtils.get('localhost', 9012, '/login?sub=b&password=b'
                , new JsonObject()) { simpleResponse ->
            token2 = simpleResponse.payload.getString("token")

            requestUtils.requestWithJwtToken(HttpMethod.GET, 'localhost'
                    , 9012, '/forward', new JsonObject(), token2) { response ->
                result2 = response
            }
        }
        TestUtils.waitResult(result1, 2000)
        TestUtils.waitResult(result2, 2000)

        then:
        token1 != token2
        result1 != result2

        1.upto(8) {
            SimpleResponse result3
            SimpleResponse result4

            requestUtils.requestWithJwtToken(HttpMethod.GET, 'localhost', 9012
                    , '/forward', new JsonObject(), token1) { response ->
                result3 = response
            }
            requestUtils.requestWithJwtToken(HttpMethod.GET, 'localhost', 9012
                    , '/forward', new JsonObject(), token2) { response ->
                result4 = response
            }

            TestUtils.waitResult(result3, 2000)
            TestUtils.waitResult(result4, 2000)

            result3 != result4

            if (ZonedDateTime.now() < now.plusSeconds(7)) {
                result3 == result1
                result4 == result2
            }
        }
    }

    private static HttpServer createMock() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(9011)

        router.route("/random1").handler(new MockHandler(vertx,
                new MockUrlConfig(expected: [
                        statusCode: 200, payload: { [random1: new Random().nextInt(100)] }]
                )
        ))

        httpServer
    }

    private static HttpServer createDest() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(9013)

        router.route('/login').handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response()
                        , 200
                        , [sub     : totalBuffer.toJsonObject().getString("sub"),
                           password: totalBuffer.toJsonObject().getString("password")])
            }
        }
        router.route().handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                JsonObject jwt = new JsonObject(requestUtils.getJwtHeader(routingContext.request()))
                JsonObject nameOfApiGateway = new JsonObject()
                        .put("nameOfApiGateway", requestUtils.getAPIGatewayNameHeader(routingContext.request()))

                Utils.fireJsonResponse(routingContext.response()
                        , 200
                        , [method: routingContext.request().method(),
                           params: new JsonObject().mergeIn(jwt).mergeIn(nameOfApiGateway)])
            }
        }

        httpServer
    }
}
