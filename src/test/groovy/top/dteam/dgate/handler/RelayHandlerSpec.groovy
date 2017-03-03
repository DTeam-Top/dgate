package top.dteam.dgate.handler

import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.FileUpload
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import spock.lang.Specification
import top.dteam.dgate.config.ApiGatewayConfig
import top.dteam.dgate.config.LoginConfig
import top.dteam.dgate.config.MockUrlConfig
import top.dteam.dgate.config.RelayUrlConfig
import top.dteam.dgate.gateway.ApiGateway
import top.dteam.dgate.gateway.SimpleResponse
import top.dteam.dgate.utils.JWTTokenGenerator
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils
import top.dteam.dgate.utils.Utils

class RelayHandlerSpec extends Specification {

    Vertx vertx
    RequestUtils requestUtils
    HttpServer destServer

    void setup() {
        vertx = Vertx.vertx()
        destServer = createDestServer()
        requestUtils = new RequestUtils(vertx)
        vertx.deployVerticle(new ApiGateway(prepareConfig()))
    }

    void cleanup() {
        vertx.close()
        destServer.close()
    }

    def "should support upload"() {
        setup:
        SimpleResponse result
        String filename = "src/test/resources/fileForUpload1"
        def size = new File(filename).size()

        when:
        sleep(100)
        requestUtils.upload(filename, 'localhost', 8080, '/uploadOne') { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.map == [filename: filename, size: size]
    }

    def "should support form"() {
        setup:
        SimpleResponse result
        JsonObject form = new JsonObject([name1: 1, name2: 2, name3: 3])

        when:
        sleep(100)
        requestUtils.form(form, 'localhost', 8080, '/form') { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.map.names.contains('name1')
        result.payload.map.names.contains('name2')
        result.payload.map.names.contains('name3')
    }

    def "should pass jwt token and name of api gateway to backend"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", 8080, '/login', new JsonObject()) { loginResponse ->
            requestUtils.requestWithJwtToken(HttpMethod.GET, "localhost", 8080, "/private", new JsonObject()
                    , loginResponse.payload.getString('token')) { simpleResponse ->
                result = simpleResponse
            }
        }

        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 200
        result.payload.map.token.sub == '13572209183'
        result.payload.map.token.name == 'foxgem'
        result.payload.map.token.role == 'normal'
        result.payload.map.name == 'testGateway'
    }

    private HttpServer createDestServer() {
        HttpServer httpServer = vertx.createHttpServer()
        Router router = Router.router(vertx)
        httpServer.requestHandler(router.&accept).listen(8081)

        router.route().handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(true))

        router.route("/uploadOne").handler { routingContext ->
            FileUpload[] uploads = routingContext.fileUploads().toArray()
            Utils.fireJsonResponse(routingContext.response(), 200, [filename: uploads[0].fileName(), size: uploads[0].size()])
        }

        router.route("/form").handler { routingContext ->
            Set<String> names = routingContext.request().formAttributes().names()
            Utils.fireJsonResponse(routingContext.response(), 200, [names: names])
        }

        router.route("/private").handler { routingContext ->
            Utils.fireJsonResponse(routingContext.response(), 200,
                    [token: new JsonObject(requestUtils.getJwtHeader(routingContext.request())),
                     name : requestUtils.getAPIGatewayNameHeader(routingContext.request())])
        }

        httpServer
    }

    private ApiGatewayConfig prepareConfig() {
        new ApiGatewayConfig(
                name: 'testGateway',
                port: 8080,
                login: new LoginConfig([url: '/login', only: ['/private']]),
                urlConfigs: [
                        new MockUrlConfig(url: "/login",
                                expected: [statusCode: 200,
                                           payload   : {
                                               JWTAuth jwtAuth = Utils.createAuthProvider(vertx)
                                               JWTTokenGenerator tokenGenerator = new JWTTokenGenerator(jwtAuth)
                                               [token: tokenGenerator.token(["sub" : "13572209183", "name": "foxgem",
                                                                             "role": "normal"], 200)]
                                           }()]),
                        new RelayUrlConfig(url: "/private", relayTo: [host: 'localhost', port: 8081]),
                        new RelayUrlConfig(url: "/uploadOne", relayTo: [host: 'localhost', port: 8081]),
                        new RelayUrlConfig(url: "/form", relayTo: [host: 'localhost', port: 8081])
                ]
        )
    }

}
