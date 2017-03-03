package top.dteam.dgate.handler

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.FileUpload
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import spock.lang.Specification
import top.dteam.dgate.config.ApiGatewayConfig
import top.dteam.dgate.config.UrlConfig
import top.dteam.dgate.gateway.ApiGateway
import top.dteam.dgate.gateway.SimpleResponse
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

        httpServer
    }

    private ApiGatewayConfig prepareConfig() {
        new ApiGatewayConfig(
                name: 'testGateway',
                port: 8080,
                urlConfigs: [
                        new UrlConfig(url: "/uploadOne", relayTo: [host: 'localhost', port: 8081]),
                        new UrlConfig(url: "/form", relayTo: [host: 'localhost', port: 8081])
                ]
        )
    }

}
