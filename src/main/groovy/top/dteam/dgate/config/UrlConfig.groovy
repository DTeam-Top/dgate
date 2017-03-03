package top.dteam.dgate.config

import groovy.transform.CompileStatic
import io.vertx.core.http.HttpMethod

@CompileStatic
abstract class UrlConfig {

    String url
    Object required
    List<HttpMethod> methods

}
