package top.dteam.dgate.config

import groovy.transform.CompileStatic

@CompileStatic
class ProxyUrlConfig extends UrlConfig{

    List<UpstreamURL> upstreamURLs

}
