package top.dteam.dgate.config

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
        List<UrlConfig> urlConfigs = new ArrayList<>()
        body.urls.keySet().each { url ->
            urlConfigs << buildUrl(url, body.urls[url])
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

    private static UrlConfig buildUrl(def key, def body) {
        String url = key
        Object required = body.required ?: null
        List<HttpMethod> methods = body.methods ?: []
        Map expected = body.expected
        List<UpstreamURL> upstreamURLs = new ArrayList<>()
        body.upstreamURLs.each { upstreamURL ->
            upstreamURLs << new UpstreamURL(host: upstreamURL.host, port: upstreamURL.port, url: upstreamURL.url,
                    before: upstreamURL.before, after: upstreamURL.after)
        }

        new UrlConfig(url: url, required: required, methods: methods, expected: expected, upstreamURLs: upstreamURLs)
    }

}
