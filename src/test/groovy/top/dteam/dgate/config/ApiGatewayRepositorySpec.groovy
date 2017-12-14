package top.dteam.dgate.config

import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import spock.lang.Specification
import top.dteam.dgate.gateway.SimpleResponse

class ApiGatewayRepositorySpec extends Specification {

    def "configuration should be parsed correctly"() {
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
                        expires = 1000
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
                            [host: 'localhost', port: 8080, url: '/test2', expires: 2000]
                        ]
                    }
                }
            }
            apiGateway2 {
                port = 7001
                host = 'localhost'
                circuitBreaker {
                    maxFailures = 5
                    timeout = 10000
                    resetTimeout = 30000
                }
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
                    "/proxy1" {
                        upstreamURLs = [
                            [host: 'localhost', port: 8080, url: '/test1']
                        ]
                    }
                    "/proxy2" {
                        upstreamURLs = [
                            [host: 'localhost', port: 8080, url: '/test1',
                             circuitBreaker: [maxFailures: 2, timeout: 3000, resetTimeout: 3000]]
                        ]
                    }
                }
            }
            apiGateway3 {
                port = 7002
                expires = 15000
                login {
                    url = '/login'
                    ignore = ['/pub']
                    refreshLimit = 2000
                    refreshExpire = 1000
                }
                urls {
                    "/login" {
                        expected {
                            statusCode = 200
                            payload = [test: true]
                        }
                    }
                    "/pub" {
                        expected {
                            statusCode = 200
                            payload = [test: true]
                        }
                    }
                    "/relayURL" {
                        relayTo {
                            host = 'localhost'
                            port = 8080
                        }
                    }
                    "/relayAnotherURL" {
                        expires = 0
                        relayTo {
                            host = 'localhost'
                            port = 8081
                            circuitBreaker = [maxFailures: 2, timeout: 3000, resetTimeout: 3000]
                        }
                    }
                }
                eventBusBridge {
                    urlPattern = '/eventbus/*'
                    consumers {
                        'address1' {
                            expected = [test1: 1]
                        }
            
                        'address2' {
                            target = 'target'
                            expected = [test2: 2]
                        }
                        'address3' {
                            timer = 1000
                            expected = [test3: 3]
                        }
                    }
                }
            }
        """
        SimpleResponse simpleResponse = new SimpleResponse([statusCode: 200, payload: new JsonObject([test: 'test'])])
        JsonObject jsonObject = new JsonObject([test: 'test'])
        ApiGatewayRepository.respository.clear()

        when:
        ApiGatewayRepository.build(config)

        then:
        ApiGatewayRepository.respository.size() == 3
        with(ApiGatewayRepository.respository[0]) {
            port == 7000
            name == 'apiGateway1'
            login.login() == '/login'
            login.refreshLimit() == LoginConfig.DEFAULT_REFRESH_LIMIT
            login.refreshExpire() == LoginConfig.DEFAULT_REFRESH_EXPIRE
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
                url == '/login'
                expires == 0
                required == ["sub", "password"]
                methods == [HttpMethod.GET, HttpMethod.POST]
                upstreamURLs.size == 1
                upstreamURLs[0].host == 'localhost'
                upstreamURLs[0].port == 8080
                upstreamURLs[0].url == '/login'
                upstreamURLs[0].expires == 0
                !upstreamURLs[0].before
                upstreamURLs[0].after
                upstreamURLs[0].circuitBreaker.maxFailures == 3
                upstreamURLs[0].circuitBreaker.timeout == 5000
                upstreamURLs[0].circuitBreaker.resetTimeout == 10000
            }
            urlConfigs[1].expected == [statusCode: 200, payload: [test: true]]
            with(urlConfigs[2]) {
                url == '/forward'
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
                upstreamURLs[0].circuitBreaker
            }
            with(urlConfigs[3]) {
                required == ['param1', 'param2']
                methods == [HttpMethod.GET, HttpMethod.POST]
                upstreamURLs.size() == 2
                upstreamURLs == [
                        new UpstreamURL(host: 'localhost', port: 8080, url: '/test1'),
                        new UpstreamURL(host: 'localhost', port: 8080, url: '/test2', expires: 2000)
                ]
                !upstreamURLs[0].before
                !upstreamURLs[0].after
                upstreamURLs[0].circuitBreaker
                !upstreamURLs[1].before
                !upstreamURLs[1].after
                upstreamURLs[1].circuitBreaker
                upstreamURLs[1].expires == 2000
            }
        }
        with(ApiGatewayRepository.respository[1]) {
            port == 7001
            host == 'localhost'
            name == 'apiGateway2'
            !login
            !cors
            urlConfigs.size() == 3
            urlConfigs[0].expected == [get   : [statusCode: 200, payload: [method: 'get']],
                                       post  : [statusCode: 200, payload: [method: 'post']],
                                       delete: [statusCode: 200, payload: [method: 'delete']]]
            urlConfigs[1].upstreamURLs[0].circuitBreaker.maxFailures == 5
            urlConfigs[1].upstreamURLs[0].circuitBreaker.timeout == 10000
            urlConfigs[1].upstreamURLs[0].circuitBreaker.resetTimeout == 30000
            urlConfigs[2].upstreamURLs[0].circuitBreaker.maxFailures == 2
            urlConfigs[2].upstreamURLs[0].circuitBreaker.timeout == 3000
            urlConfigs[2].upstreamURLs[0].circuitBreaker.resetTimeout == 3000
        }
        with(ApiGatewayRepository.respository[2]) {
            port == 7002
            host == '0.0.0.0'
            name == 'apiGateway3'
            login
            login.login() == '/login'
            login.ignore() == ['/pub']
            login.refreshLimit() == 2000
            login.refreshExpire() == 1000
            !login.only()
            !cors
            urlConfigs.size() == 4
            urlConfigs[2].relayTo.host == 'localhost'
            urlConfigs[2].relayTo.port == 8080
            urlConfigs[2].relayTo.circuitBreaker.maxFailures == 3
            urlConfigs[2].relayTo.circuitBreaker.timeout == 5000
            urlConfigs[2].relayTo.circuitBreaker.resetTimeout == 10000
            urlConfigs[2].expires == 15000
            urlConfigs[3].relayTo.host == 'localhost'
            urlConfigs[3].relayTo.port == 8081
            urlConfigs[3].relayTo.circuitBreaker.maxFailures == 2
            urlConfigs[3].relayTo.circuitBreaker.timeout == 3000
            urlConfigs[3].relayTo.circuitBreaker.resetTimeout == 3000
            urlConfigs[3].expires == 0
            with(eventBusBridgeConfig) {
                urlPattern == '/eventbus/*'
                consumers.size == 3
                consumers[0].address == 'address1'
                !consumers[0].target
                consumers[0].expected == [test1: 1]
                consumers[0].timer == 0
                consumers[1].address == 'address2'
                consumers[1].target == 'target'
                consumers[1].expected == [test2: 2]
                consumers[1].timer == 0
                consumers[2].address == 'address3'
                !consumers[2].target
                consumers[2].expected == [test3: 3]
                consumers[2].timer == 1000

            }
        }
    }

}
