package top.dteam.dgate.config

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.vertx.core.http.HttpMethod

@Slf4j
@CompileStatic
class UrlConfig {

    public static final int PROXY = 0
    public static final int MOCK = 1
    public static final int RELAY = 2

    String url
    List<UpstreamURL> upstreamURLs
    Object required
    List<HttpMethod> methods
    Map expected
    RelayTo relayTo

    boolean validate() {
        if ((upstreamURLs && expected) || (!upstreamURLs && !expected)) {
            log.error("upStreamURLs和expected同时存在,或同时为空!")
            false
        }

        true
    }

    int requestHandlerType() {
        if (expected) {
            MOCK
        } else if (upstreamURLs) {
            PROXY
        } else if (relayTo) {
            RELAY
        } else {
            -1
        }
    }
}
