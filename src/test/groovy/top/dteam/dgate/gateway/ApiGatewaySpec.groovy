package top.dteam.dgate.gateway

import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.Router
import spock.lang.Specification
import spock.lang.Unroll
import top.dteam.dgate.config.ApiGatewayConfig
import top.dteam.dgate.config.UpstreamURL
import top.dteam.dgate.config.UrlConfig
import top.dteam.dgate.utils.JWTTokenGenerator
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils
import top.dteam.dgate.utils.Utils

class ApiGatewaySpec extends Specification {

    private static final int GATEWAY_PORT = 7000
    private static final int GATEWAY_PORT_WITH_LOGIN = 7001

    Vertx vertx
    HttpServer destServer
    RequestUtils requestUtils

    void setup() {
        vertx = Vertx.vertx()
        destServer = createDestServer()
        requestUtils = new RequestUtils(vertx)
        vertx.deployVerticle(new ApiGateway(prepareConfig()))
        vertx.deployVerticle(new ApiGateway(prepareConfigWithMockLogin()))
    }

    void cleanup() {
        vertx.close()
        destServer.close()
    }

    @Unroll
    def "[#url]应该可以正常工作"() {
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

    def "应该可以mock login并得到jwt token"() {
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
                urlConfigs: [new UrlConfig(url: "/mock", expected: [statusCode: 200,
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
                             new UrlConfig(url: "/mock-get", expected: [get: [statusCode: 200, payload: [method: 'get']]]),
                             new UrlConfig(url: "/forward", upstreamURLs: [
                                     new UpstreamURL(host: "localhost", port: 8082, url: "/test1")]),
                             new UrlConfig(url: "/composite", upstreamURLs: [
                                     new UpstreamURL(host: "localhost", port: 8082, url: "/test1"),
                                     new UpstreamURL(host: "localhost", port: 8082, url: "/test2")])]
        )
    }

    private ApiGatewayConfig prepareConfigWithMockLogin() {
        new ApiGatewayConfig(
                name: 'testGateway',
                port: GATEWAY_PORT_WITH_LOGIN,
                login: '/login',
                urlConfigs: [new UrlConfig(url: "/login",
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

}
