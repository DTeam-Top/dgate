package top.dteam.dgate

import top.dteam.dgate.config.ApiGatewayRepository
import top.dteam.dgate.gateway.ApiGateway
import top.dteam.dgate.gateway.SimpleResponse
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils
import top.dteam.dgate.utils.Utils
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll

@Stepwise
class ApiGatewayIntegationSpec extends Specification {

    static Vertx vertx
    static HttpServer dest
    static RequestUtils requestUtils

    static String config = """
        import io.vertx.core.http.HttpMethod

        apiGateway {
            port = 7000
            login = "/login"
            urls {
                "/login" {
                    required = ["sub", "password"]
                    methods = [HttpMethod.GET, HttpMethod.POST]
                    upstreamURLs = [
                        [
                            host: 'localhost', port: 8080, url: '/login',
                            after: { simpleResponse ->
                                Map payload = [
                                    sub: simpleResponse.payload.getString("sub"),
                                    name: simpleResponse.payload.getString("name"),
                                    role: simpleResponse.payload.getString("role")
                                ]
                                simpleResponse.payload.put('token', delegate.tokenGenerator.token(payload, 5))
                                simpleResponse
                            }
                        ]
                    ]
                }
                "/forward" {
                    required = ['param']
                    methods = [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE]
                    upstreamURLs = [
                        [
                            host: 'localhost', port: 8080, url: '/test',
                            before: { jsonObject -> jsonObject },
                            after: { simpleResponse -> simpleResponse }
                        ]
                    ]
                }
            }
        }
    """

    static String token

    void setupSpec() {
        vertx = Vertx.vertx()
        deployGate()
        dest = createDest()
        requestUtils = new RequestUtils(vertx)
    }

    void cleanupSpec() {
        dest.close()
        vertx.close()
    }

    def "未登录访问/forward应该返回401"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", 7000, "/forward", new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 401
    }

    def "使用不支持方法访问/login应该返回400"() {
        setup:
        SimpleResponse result

        when:
        requestUtils.request(HttpMethod.PUT, "localhost", 7000, "/login", new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 400
    }

    @Unroll
    def "访问/login但未给全参数应该返回400"() {
        setup:
        SimpleResponse result

        when:
        requestUtils.get("localhost", 7000, url, new JsonObject(params)) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 400

        where:
        url                        | params
        '/login'                   | [:]
        '/login'                   | [sub: '13572209183']
        '/login'                   | [password: 'password']
        '/login?sub=13572209183'   | [:]
        '/login?password=password' | [:]
    }

    @Unroll
    def "访问/login成功后应该有jwtToken"() {
        setup:
        SimpleResponse result

        when:
        requestUtils.get("localhost", 7000, url, new JsonObject(params)) { simpleResponse ->
            result = simpleResponse
            token = simpleResponse.payload.getString('token')
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        token

        where:
        url                                         | params
        '/login?sub=13572209183&&password=password' | [:]
        '/login'                                    | [sub: '13572209183', password: 'password']
    }

    def "登录成功后应该能访问/forward且jwtToken和api gateway的名字都能传给它"() {
        setup:
        SimpleResponse result

        when:
        requestUtils.requestWithJwtToken(HttpMethod.POST, "localhost", 7000, "/forward",
                new JsonObject([param: 'param']), token) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.map.method == HttpMethod.POST.toString()
        result.payload.map.params.param == 'param'
        result.payload.map.params.nameOfApiGateway == 'apiGateway'
        result.payload.map.params.token.sub == '13572209183'
        result.payload.map.params.token.name == 'foxgem'
        result.payload.map.params.token.role == 'normal'
    }

    def "超时之后无法用同样的token访问/forward"() {
        setup:
        SimpleResponse result

        when:
        sleep(5000)
        requestUtils.requestWithJwtToken(HttpMethod.POST, "localhost", 7000, "/forward",
                new JsonObject([param: 'param']), token) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 401
    }

    private HttpServer createDest() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8080)

        router.route('/login').handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 200, [sub : '13572209183',
                                                                        name: 'foxgem', role: 'normal'])
            }
        }
        router.route().handler { routingContext ->
            routingContext.request().bodyHandler { totalBuffer ->
                Utils.fireJsonResponse(routingContext.response(), 200,
                        [method: routingContext.request().method(),
                         params: totalBuffer.toJsonObject()])
            }
        }

        httpServer
    }

    private void deployGate() {
        vertx.deployVerticle(new ApiGateway(ApiGatewayRepository.build(config)[0]))
    }

}
