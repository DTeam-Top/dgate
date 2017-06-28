package top.dteam.dgate.config

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.json.JsonObject
import top.dteam.dgate.gateway.SimpleResponse

@EqualsAndHashCode(excludes = ['before', 'after', 'circuitBreaker'])
@CompileStatic
class UpstreamURL {

    String host
    int port
    String url
    int expires = 0
    CircuitBreakerOptions circuitBreaker

    Closure<JsonObject> before
    Closure<SimpleResponse> after

    String resolve(JsonObject context) {
        String result = resolveParams(getParamsFromUrl(url), context)
        verifyUrl(result) ?: '/'
    }

    private static List<String> getParamsFromUrl(String url) {
        Arrays.asList(url.split('/')).findAll { part -> part.startsWith(':') }
    }

    private String resolveParams(List<String> params, JsonObject context) {
        String result = url

        params?.each { param ->
            String value = param.endsWith('?') ? context.getValue(param[1..-2]) : context.getValue(param[1..-1])
            if (value) {
                result = result.replace(param, value)
            }
        }

        result
    }

    private static String verifyUrl(String url) {
        String result = url

        List<String> unresolvedParams = getParamsFromUrl(result)
        unresolvedParams.reverseEach { unresolvedParam ->
            if (unresolvedParam.endsWith('?') && result.endsWith(unresolvedParam)) {
                result = result - "/${unresolvedParam}"
            } else {
                throw new InvalidConfiguriationException("无效的URL格式,参数值或格式不对")
            }
        }

        result
    }

    @Override
    String toString() {
        "$host-$port-$url"
    }

}
