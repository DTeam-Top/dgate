package top.dteam.dgate.config

import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.http.HttpMethod

class ApiGatewayRepository {

    @Delegate
    List<ApiGatewayConfig> respository

    ApiGatewayRepository() {
        this.respository = new ArrayList<>()
    }

    static ApiGatewayRepository load(String file = System.getProperty('conf')) {
        if (!file) {
            throw new FileNotFoundException("Please set config file first!")
        }

        String config
        config = new File(file).text
        build(config)
    }

    static ApiGatewayRepository build(String config) {
        ApiGatewayRepository apiGatewayRepository = new ApiGatewayRepository()

        ConfigSlurper slurper = new ConfigSlurper()
        ConfigObject configObject = slurper.parse(config)
        configObject.keySet().each { apiGateway ->
            apiGatewayRepository.respository << buildApiGateway(apiGateway, configObject[apiGateway])
        }

        apiGatewayRepository
    }

    private static ApiGatewayConfig buildApiGateway(def key, def body) {
        String name = key
        int port = body.port
        String host = body.host ?: '0.0.0.0'
        LoginConfig login = body.login ? buildLogin(body.login) : null
        CorsConfig cors = buildCors(body.cors)
        CircuitBreakerOptions defaultCBOptions = buildCircuitBreaker(body.circuitBreaker)
        List<UrlConfig> urlConfigs = new ArrayList<>()
        body.urls.keySet().each { url ->
            urlConfigs << buildUrl(url, body.urls[url], defaultCBOptions)
        }

        new ApiGatewayConfig(
                name: name,
                port: port,
                host: host,
                urlConfigs: urlConfigs,
                login: login,
                cors: cors
        )
    }

    private static LoginConfig buildLogin(def login) {
        new LoginConfig(login)
    }

    private static CorsConfig buildCors(Map cors) {
        if (cors) {
            new CorsConfig(cors)
        } else {
            null
        }
    }

    private static CircuitBreakerOptions buildCircuitBreaker(Map circuitBreaker) {
        new CircuitBreakerOptions()
                .setMaxFailures(circuitBreaker?.maxFailures ?: 3)
                .setTimeout(circuitBreaker?.timeout ?: 5000)
                .setResetTimeout(circuitBreaker?.resetTimeout ?: 10000)
    }

    private static UrlConfig buildUrl(def key, def body, CircuitBreakerOptions cbOptions) {
        String url = key
        Object required = body.required ?: null
        List<HttpMethod> methods = body.methods ?: []
        Map expected = body.expected
        List<UpstreamURL> upstreamURLs = new ArrayList<>()
        body.upstreamURLs.each { upstreamURL ->
            CircuitBreakerOptions cbOptionsForUpstreamURL =
                    upstreamURL.circuitBreaker ? buildCircuitBreaker(upstreamURL.circuitBreaker) : cbOptions

            upstreamURLs << new UpstreamURL(host: upstreamURL.host, port: upstreamURL.port, url: upstreamURL.url,
                    before: upstreamURL.before, after: upstreamURL.after, cbOptions: cbOptionsForUpstreamURL)
        }

        Map relayTo = body.relayTo

        if (expected) {
            return new MockUrlConfig(url: url, required: required, methods: methods, expected: expected)
        } else if (upstreamURLs) {
            return new ProxyUrlConfig(url: url, required: required, methods: methods, upstreamURLs: upstreamURLs)
        } else if (relayTo) {
            CircuitBreakerOptions cbOptionsForRelayTo =
                    relayTo.circuitBreaker ? buildCircuitBreaker(relayTo.circuitBreaker) : cbOptions
            return new RelayUrlConfig(url: url,
                    relayTo: new RelayTo(host: relayTo.host, port: relayTo.port, cbOptions: cbOptionsForRelayTo))
        } else {
            throw new InvalidConfiguriationException('Unknown URL type!')
        }

    }

}
