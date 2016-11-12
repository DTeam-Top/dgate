package top.dteam.dgate.config

import groovy.transform.CompileStatic
import io.vertx.core.http.HttpMethod

@CompileStatic
class CorsConfig {

    String allowedOriginPattern
    Set<String> allowedHeaders
    Set<HttpMethod> allowedMethods
    Set<String> exposedHeaders
    Integer maxAgeSeconds
    Boolean allowCredentials

}
