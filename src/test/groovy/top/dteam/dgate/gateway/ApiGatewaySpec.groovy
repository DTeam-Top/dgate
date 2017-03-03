package top.dteam.dgate.gateway

import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.Router
import spock.lang.Specification
import spock.lang.Unroll
import top.dteam.dgate.config.*
import top.dteam.dgate.utils.JWTTokenGenerator
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils
import top.dteam.dgate.utils.Utils

class ApiGatewaySpec extends Specification {

    private static final int GATEWAY_PORT = 7000
    private static final int GATEWAY_PORT_WITH_LOGIN = 7001
    private static final int GATEWAY_PORT_WITH_LOGIN_IGNORE = 7002
    private static final int GATEWAY_PORT_WITH_LOGIN_ONLY = 7003

    Vertx vertx
    HttpServer destServer
    RequestUtils requestUtils

    void setup() {
        vertx = Vertx.vertx()
        destServer = createDestServer()
        requestUtils = new RequestUtils(vertx)
        vertx.deployVerticle(new ApiGateway(prepareConfig()))
        vertx.deployVerticle(new ApiGateway(prepareConfigWithMockLogin()))
        vertx.deployVerticle(new ApiGateway(prepareConfigWithLoginConfigConatainingIgnore()))
        vertx.deployVerticle(new ApiGateway(prepareConfigWithLoginConfigConatainingOnly()))
    }

    void cleanup() {
        vertx.close()
        destServer.close()
    }

    @Unroll
    def "[#url] should work"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", GATEWAY_PORT, url, new JsonObject([method: "get"])) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == statusCode
        result.payload.toString() == new JsonObject(response).toString()

        where:
        url          | statusCode                                        | response
        '/mock'      | prepareConfig().urlConfigs[0].expected.statusCode | prepareConfig().urlConfigs[0].expected.payload
        '/mock-get'  | 200                                               | [method: 'get']
        '/forward'   | 200                                               | [method1: HttpMethod.GET, params1: [method: "get", nameOfApiGateway: 'testGateway']]
        '/composite' | 200                                               | [method1: HttpMethod.GET, params1: [method: "get", nameOfApiGateway: 'testGateway'],
                                                                            method2: HttpMethod.GET, params2: [method: "get", nameOfApiGateway: 'testGateway']]
    }

    def "could mock login and get a jwt token"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", GATEWAY_PORT_WITH_LOGIN, '/login', new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.getString('token').split(/\./).size() == 3
    }

    @Unroll
    def "could only access those urls in ignore list in login block directly: #url"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", GATEWAY_PORT_WITH_LOGIN_IGNORE, url, new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == statusCode

        where:
        url            | statusCode
        '/mock-ignore' | 200
        '/mock'        | 401
        '/login'       | 200
    }

    @Unroll
    def "could only access those urls not in only list in login block directly: #url"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", GATEWAY_PORT_WITH_LOGIN_ONLY, url, new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == statusCode

        where:
        url          | statusCode
        '/mock-only' | 401
        '/mock'      | 200
        '/login'     | 200
    }

    private HttpServer createDestServer() {
        HttpServer httpServer = vertx.createHttpServer()
        Router destRouter = Router.router(vertx)

        destRouter.route("/test1").handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 200,
                        [method1: routingContext.request().method(),
                         params1: totalBuffer.toJsonObject()])
            }
        }

        destRouter.route("/test2").handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 200,
                        [method2: routingContext.request().method(),
                         params2: totalBuffer.toJsonObject()])
            }
        }

        httpServer.requestHandler(destRouter.&accept).listen(8082)

        httpServer
    }

    private ApiGatewayConfig prepareConfig() {
        new ApiGatewayConfig(
                name: 'testGateway',
                port: GATEWAY_PORT,
                urlConfigs: [new MockUrlConfig(url: "/mock", expected: [statusCode: 200,
                                                                        payload   : [
                                                                                eqLocations       : [],
                                                                                opRateInLast30Days: [],
                                                                                myOrgs            : [
                                                                                        [
                                                                                                "name" : "org1",
                                                                                                "admin": false
                                                                                        ]
                                                                                ]
                                                                        ]]),
                             new MockUrlConfig(url: "/mock-get", expected: [get: [statusCode: 200, payload: [method: 'get']]]),
                             new ProxyUrlConfig(url: "/forward", upstreamURLs: [
                                     new UpstreamURL(host: "localhost", port: 8082, url: "/test1")]),
                             new ProxyUrlConfig(url: "/composite", upstreamURLs: [
                                     new UpstreamURL(host: "localhost", port: 8082, url: "/test1"),
                                     new UpstreamURL(host: "localhost", port: 8082, url: "/test2")])]
        )
    }

    private ApiGatewayConfig prepareConfigWithMockLogin() {
        new ApiGatewayConfig(
                name: 'testGateway',
                port: GATEWAY_PORT_WITH_LOGIN,
                login: new LoginConfig('/login'),
                urlConfigs: [new MockUrlConfig(url: "/login",
                        expected: [statusCode: 200,
                                   payload   : {
                                       JWTAuth jwtAuth = Utils.createAuthProvider(vertx)
                                       JWTTokenGenerator tokenGenerator = new JWTTokenGenerator(jwtAuth)
                                       [token: tokenGenerator.token(["sub" : "13572209183", "name": "foxgem",
                                                                     "role": "normal"], 200)]
                                   }()
                        ])]
        )
    }

    private ApiGatewayConfig prepareConfigWithLoginConfigConatainingIgnore() {
        new ApiGatewayConfig(
                name: 'testGateway',
                port: GATEWAY_PORT_WITH_LOGIN_IGNORE,
                login: new LoginConfig([url: '/login', ignore: ['/mock-ignore']]),
                urlConfigs: [new MockUrlConfig(url: "/mock-ignore", expected: [statusCode: 200,
                                                                               payload   : [ignore: true]]),
                             new MockUrlConfig(url: "/mock", expected: [statusCode: 200,
                                                                        payload   : [ignore: false]]),
                             new MockUrlConfig(url: "/login",
                                     expected: [statusCode: 200,
                                                payload   : {
                                                    JWTAuth jwtAuth = Utils.createAuthProvider(vertx)
                                                    JWTTokenGenerator tokenGenerator = new JWTTokenGenerator(jwtAuth)
                                                    [token: tokenGenerator.token(["sub" : "13572209183", "name": "foxgem",
                                                                                  "role": "normal"], 200)]
                                                }()])
                ]
        )
    }

    private ApiGatewayConfig prepareConfigWithLoginConfigConatainingOnly() {
        new ApiGatewayConfig(
                name: 'testGateway',
                port: GATEWAY_PORT_WITH_LOGIN_ONLY,
                login: new LoginConfig([url: '/login', only: ['/mock-only']]),
                urlConfigs: [new MockUrlConfig(url: "/login",
                        expected: [statusCode: 200,
                                   payload   : {
                                       JWTAuth jwtAuth = Utils.createAuthProvider(vertx)
                                       JWTTokenGenerator tokenGenerator = new JWTTokenGenerator(jwtAuth)
                                       [token: tokenGenerator.token(["sub" : "13572209183", "name": "foxgem",
                                                                     "role": "normal"], 200)]
                                   }()]),
                             new MockUrlConfig(url: "/mock-only", expected: [statusCode: 200,
                                                                             payload   : [only: true]]),
                             new MockUrlConfig(url: "/mock", expected: [statusCode: 200,
                                                                        payload   : [only: false]]),
                ]
        )
    }

}
