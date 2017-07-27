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

    def "should get 401 when request /forward before login"() {
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

    def "should get 400 when request /login with unsupported HTTP methods"() {
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
    def "should get 400 when request /login with required not in the request"() {
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
    def "should get a jwt token when request /login successfully"() {
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

    def "should get 200 and a payload including a jwt token & the name of api gateway when request /forward after login"() {
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
        result.payload.map.params.sub == '13572209183'
        result.payload.map.params.name == 'foxgem'
        result.payload.map.params.role == 'normal'
    }

    def "should get 401 when request /forward with an expired jwt token "() {
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
                JsonObject jwt = new JsonObject(requestUtils.getJwtHeader(routingContext.request()))
                JsonObject nameOfApiGateway = new JsonObject()
                        .put("nameOfApiGateway", requestUtils.getAPIGatewayNameHeader(routingContext.request()))

                Utils.fireJsonResponse(routingContext.response(), 200,
                        [method: routingContext.request().method(),
                         params: totalBuffer.toJsonObject().mergeIn(jwt).mergeIn(nameOfApiGateway)])
            }
        }

        httpServer
    }

    private void deployGate() {
        ApiGatewayRepository.respository.clear()
        ApiGatewayRepository.build(config)
        vertx.deployVerticle(new ApiGateway(ApiGatewayRepository.respository[0]))
    }

}
