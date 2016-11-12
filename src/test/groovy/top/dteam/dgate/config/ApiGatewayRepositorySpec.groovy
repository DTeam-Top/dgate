package top.dteam.dgate.config

import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import spock.lang.Specification
import top.dteam.dgate.gateway.SimpleResponse

class ApiGatewayRepositorySpec extends Specification {

    def "应该能正确地根据配置内容解析"() {
        setup:
        String config = """
            import io.vertx.core.http.HttpMethod

            apiGateway1 {
                port = 7000
                login = "/login"
                cors {
                    allowedOriginPattern = "http://127.0.0.1"
                    allowedMethods = [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE]
                    allowedHeaders = ['X-PINGOTHER']
                    exposedHeaders = ['X-PINGOTHER']
                    allowCredentials = false
                    maxAgeSeconds = 1728000
                }
                urls {
                    "/login" {
                        required = ["sub", "password"]
                        methods = [HttpMethod.GET, HttpMethod.POST]
                        upstreamURLs = [
                            [
                                host: 'localhost', port: 8080, url: '/login',
                                after: { simpleResponse ->
                                    simpleResponse.put(tokenGenerator.token(["sub": "13572209183", "name": "foxgem",
                                                                             "role": "normal"], 2))
                                    simpleResponse
                                }
                            ]
                        ]
                    }
                    "/mock" {
                        expected {
                            statusCode = 200
                            payload = [test: true]
                        }
                    }
                    "/forward" {
                        required = [get: ['param1'], post: ['param2'], delete: ['param3']]
                        methods = [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE]
                        upstreamURLs = [
                            [
                                host: 'localhost', port: 8080, url: '/test',
                                before: { jsonObject -> jsonObject },
                                after: { simpleResponse -> simpleResponse }
                            ]
                        ]
                    }
                    "/composite" {
                        required = ['param1', 'param2']
                        methods = [HttpMethod.GET, HttpMethod.POST]
                        upstreamURLs = [
                            [host: 'localhost', port: 8080, url: '/test1'],
                            [host: 'localhost', port: 8080, url: '/test2']
                        ]
                    }
                }
            }
            apiGateway2 {
                port = 7001
                urls {
                    "/mock" {
                        expected {
                            get {
                                statusCode = 200
                                payload = [method: 'get']
                            }
                            post {
                                statusCode = 200
                                payload = [method: 'post']
                            }
                            delete {
                                statusCode = 200
                                payload = [method: 'delete']
                            }
                        }
                    }
                }
            }
        """
        SimpleResponse simpleResponse = new SimpleResponse([statusCode: 200, payload: new JsonObject([test: 'test'])])
        JsonObject jsonObject = new JsonObject([test: 'test'])

        when:
        ApiGatewayRepository repository = ApiGatewayRepository.build(config)

        then:
        repository.size() == 2
        with(repository[0]) {
            port == 7000
            name == 'apiGateway1'
            login == '/login'
            with(cors) {
                allowedOriginPattern == "http://127.0.0.1"
                allowedMethods == new HashSet<>([HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE])
                allowedHeaders == new HashSet<>(['X-PINGOTHER'])
                exposedHeaders == new HashSet<>(['X-PINGOTHER'])
                allowCredentials == false
                maxAgeSeconds == 1728000
            }
            urlConfigs.size() == 4
            with(urlConfigs[0]) {
                required == ["sub", "password"]
                methods == [HttpMethod.GET, HttpMethod.POST]
                upstreamURLs.size == 1
                upstreamURLs[0].host == 'localhost'
                upstreamURLs[0].port == 8080
                upstreamURLs[0].url == '/login'
                !upstreamURLs[0].before
                upstreamURLs[0].after
            }
            urlConfigs[1].expected == [statusCode: 200, payload: [test: true]]
            with(urlConfigs[2]) {
                required == [get: ['param1'], post: ['param2'], delete: ['param3']]
                methods == [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE]
                upstreamURLs.size == 1
                upstreamURLs[0].host == 'localhost'
                upstreamURLs[0].port == 8080
                upstreamURLs[0].url == '/test'
                upstreamURLs[0].before
                upstreamURLs[0].before(jsonObject) == jsonObject
                upstreamURLs[0].after
                upstreamURLs[0].after(simpleResponse) == simpleResponse
                !expected
            }
            with(urlConfigs[3]) {
                required == ['param1', 'param2']
                methods == [HttpMethod.GET, HttpMethod.POST]
                upstreamURLs.size() == 2
                upstreamURLs == [
                        new UpstreamURL(host: 'localhost', port: 8080, url: '/test1'),
                        new UpstreamURL(host: 'localhost', port: 8080, url: '/test2')
                ]
                !upstreamURLs[0].before
                !upstreamURLs[1].after
                !upstreamURLs[0].before
                !upstreamURLs[1].after
                !expected
            }
        }
        with(repository[1]) {
            port == 7001
            name == 'apiGateway2'
            !login
            !cors
            urlConfigs.size() == 1
            urlConfigs[0].expected == [get   : [statusCode: 200, payload: [method: 'get']],
                                       post  : [statusCode: 200, payload: [method: 'post']],
                                       delete: [statusCode: 200, payload: [method: 'delete']]]
        }
    }

}
