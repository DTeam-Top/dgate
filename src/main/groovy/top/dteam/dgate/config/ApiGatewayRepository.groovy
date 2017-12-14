package top.dteam.dgate.config

import groovy.io.FileType
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.http.HttpMethod

class ApiGatewayRepository {

    @Delegate
    static List<ApiGatewayConfig> respository = new ArrayList<>()

    static void load(String file = System.getProperty('conf')) {
        respository.clear()

        if (!file) {
            throw new FileNotFoundException("Please set config file first!")
        }

        File f = new File(file)
        if (f.isFile()) {
            build(f.text)
        } else if (f.isDirectory()) {
            // Matching files' name end with .conf, treat as config files.
            f.eachFileMatch(FileType.FILES, ~/.*\.conf$/) {
                build(it.text)
            }
        } else {
            throw new FileNotFoundException("No such file or directory: ${file}")
        }
    }

    static void build(String config) {
        ConfigSlurper slurper = new ConfigSlurper()
        ConfigObject configObject = slurper.parse(config)
        configObject.keySet().each { apiGateway ->
            respository << buildApiGateway(apiGateway, configObject[apiGateway])
        }
    }

    private static ApiGatewayConfig buildApiGateway(def key, def body) {
        String name = key
        int port = body.port
        String host = body.host ?: '0.0.0.0'
        int expires = body.expires ?: 0
        LoginConfig login = body.login ? buildLogin(body.login) : null
        CorsConfig cors = buildCors(body.cors as Map)
        CircuitBreakerOptions defaultCBOptions = buildCircuitBreaker(body.circuitBreaker as Map)
        List<UrlConfig> urlConfigs = new ArrayList<>()
        body.urls.keySet().each { url ->
            urlConfigs << buildUrl(url, body.urls[url], defaultCBOptions, expires)
        }
        EventBusBridgeConfig eventBusBridgeConfig = buildEventBusBridge(body.eventBusBridge as Map)

        new ApiGatewayConfig(
                name: name,
                port: port,
                host: host,
                urlConfigs: urlConfigs,
                login: login,
                cors: cors,
                eventBusBridgeConfig: eventBusBridgeConfig
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

    private static UrlConfig buildUrl(
            def key, def body, CircuitBreakerOptions defaultCBOptions, int defaultExpires = 0) {
        String url = key
        int expires = body.expires != [:] ? body.expires : defaultExpires
        // To avoid expires = 0 but defaultExpires != 0
        Object required = body.required ?: null
        List<HttpMethod> methods = (body.methods && body.methods instanceof List) ?
                parseMethods(body.methods as List) : []
        Map expected = body.expected
        List<UpstreamURL> upstreamURLs = new ArrayList<>()
        body.upstreamURLs.each { upstreamURL ->
            upstreamURL.expires = upstreamURL.expires != null ? upstreamURL.expires : expires
            CircuitBreakerOptions cbOptionsForUpstreamURL =
                    upstreamURL.circuitBreaker ?
                            buildCircuitBreaker(upstreamURL.circuitBreaker as Map) :
                            defaultCBOptions

            upstreamURL << [circuitBreaker: cbOptionsForUpstreamURL]
            upstreamURLs << new UpstreamURL(upstreamURL)
        }

        Map relayTo = body.relayTo

        if (expected) {
            return new MockUrlConfig(url: url,
                    required: required,
                    methods: methods,
                    expected: expected)
        } else if (upstreamURLs) {
            return new ProxyUrlConfig(url: url,
                    required: required,
                    methods: methods,
                    expires: expires,
                    upstreamURLs: upstreamURLs)
        } else if (relayTo) {
            CircuitBreakerOptions cbOptionsForRelayTo =
                    relayTo.circuitBreaker ? buildCircuitBreaker(relayTo.circuitBreaker) : defaultCBOptions

            relayTo << [circuitBreaker: cbOptionsForRelayTo]
            return new RelayUrlConfig(url: url, expires: expires,
                    relayTo: new RelayTo(relayTo))
        } else {
            throw new InvalidConfiguriationException('Unknown URL type!')
        }

    }

    private static List<HttpMethod> parseMethods(List methods) {
        List<HttpMethod> parsedMethods = []

        methods.each {
            if (it instanceof String) {
                parsedMethods.add(HttpMethod.valueOf(it))
            } else if (it instanceof HttpMethod) {
                parsedMethods.add(it)
            } else {
                throw new InvalidConfiguriationException("Unknown method config '${it}'!")
            }
        }

        parsedMethods
    }

    private static EventBusBridgeConfig buildEventBusBridge(Map eventBusBridge) {
        if (!eventBusBridge) {
            null
        } else {
            EventBusBridgeConfig config = new EventBusBridgeConfig()

            if (eventBusBridge.urlPattern && eventBusBridge.consumers) {
                config.urlPattern = eventBusBridge.urlPattern
                config.consumers = new ArrayList<>()
                eventBusBridge.consumers.keySet().each { address ->
                    config.consumers << new Consumer(
                            address: address,
                            target: eventBusBridge.consumers."$address".target ?: null,
                            expected: eventBusBridge.consumers."$address".expected,
                            timer: eventBusBridge.consumers."$address".timer ?: 0
                    )
                }
            } else {
                throw new InvalidConfiguriationException('no URL Pattern or Consumers in EventBusBridge!')
            }

            config
        }
    }
}
