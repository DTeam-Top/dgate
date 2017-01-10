package top.dteam.dgate.config

import groovy.transform.CompileStatic

@CompileStatic
class ApiGatewayConfig {

    String name
    int port
    LoginConfig login
    CorsConfig cors
    List<UrlConfig> urlConfigs

}
