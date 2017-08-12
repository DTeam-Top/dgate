package top.dteam.dgate.handler

import io.vertx.core.MultiMap
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpServer
import spock.lang.Specification
import top.dteam.dgate.config.ApiGatewayRepository
import top.dteam.dgate.gateway.ApiGateway
import top.dteam.dgate.utils.TestUtils

class RequestHeadersSpec extends Specification {
    private static final Vertx vertx = Vertx.vertx()
    private static MultiMap headers
    private static final HttpServer backend = vertx
            .createHttpServer().requestHandler({ request ->
        headers = request.headers()
        request.response().end('{"result": "OK"}')
    }).listen(7777)

    private static final CONFIG = '''
        testgateway {
            port = 7776
            urls {
                "/proxy" {
                    upstreamURLs = [
                        [host: 'localhost', port: 7777, url: '/proxy']
                    ]
                }
                "/relay" {
                    relayTo {
                        host = 'localhost'
                        port = 7777
                    }
                }
            }
        }
    '''

    void setupSpec() {
        ApiGatewayRepository.respository.clear()
        ApiGatewayRepository.build(CONFIG)
        ApiGatewayRepository.respository.each {
            vertx.deployVerticle(new ApiGateway(it))
        }
    }

    void cleanSpec() {
        backend.close()
        vertx.close()
    }

    def "Should get correct proxy headers if client isn't from proxy"(String requestURI) {
        setup:
        HttpClientResponse clientResponse
        HttpClient client = vertx.createHttpClient()
        HttpClientRequest request = client.get(7776, "localhost", requestURI) { response ->
            clientResponse = response
        }
        request.putHeader("User-Agent", "Dgate Test")
        request.end()

        TestUtils.waitResult(headers, 5000)
        TestUtils.waitResult(clientResponse, 5000)

        expect:
        headers.get("User-Agent") == "Dgate Test"
        headers.get("X-Real-IP") == "127.0.0.1"
        headers.get("X-Forwarded-For") == "127.0.0.1"
        headers.get("X-Forwarded-Host") == "localhost:7776"
        headers.get("X-Forwarded-Proto") == "http"

        cleanup:
        headers = null

        where:
        requestURI | _
        "/proxy"   | _
        "/relay"   | _
    }

    def "Should get correct proxy headers if client is from proxy"(String requestURI) {
        setup:
        HttpClientResponse clientResponse
        HttpClient client = vertx.createHttpClient()
        HttpClientRequest request = client.get(7776, "localhost", requestURI) { response ->
            clientResponse = response
        }
        request.putHeader("User-Agent", "Dgate Test Via Proxy")
                .putHeader("X-Real-IP", "8.8.8.8")
                .putHeader("X-Forwarded-For", "8.8.8.8,8.8.4.4")
                .end()

        TestUtils.waitResult(headers, 5000)
        TestUtils.waitResult(clientResponse, 5000)

        expect:
        headers.get("User-Agent") == "Dgate Test Via Proxy"
        headers.get("X-Real-IP") == "8.8.8.8"
        headers.get("X-Forwarded-For") == "8.8.8.8,8.8.4.4,127.0.0.1"
        headers.get("X-Forwarded-Host") == "localhost:7776"
        headers.get("X-Forwarded-Proto") == "http"

        cleanup:
        headers = null

        where:
        requestURI | _
        "/proxy"   | _
        "/relay"   | _
    }
}
