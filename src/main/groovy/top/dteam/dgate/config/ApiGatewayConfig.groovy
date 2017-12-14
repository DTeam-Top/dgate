package top.dteam.dgate.config

import groovy.transform.CompileStatic

@CompileStatic
class ApiGatewayConfig {

    String name
    int port
    String host = '0.0.0.0'
    LoginConfig login
    CorsConfig cors
    List<UrlConfig> urlConfigs
    EventBusBridgeConfig eventBusBridgeConfig

}
