package top.dteam.dgate.config

import groovy.transform.CompileStatic

@CompileStatic
class EventBusBridgeConfig {

    String urlPattern
    List<Publisher> publishers
    List<Consumer> consumers

}
