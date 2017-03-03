package top.dteam.dgate.config

import groovy.transform.CompileStatic
import io.vertx.circuitbreaker.CircuitBreakerOptions

@CompileStatic
class RelayTo {

    String host
    int port
    CircuitBreakerOptions cbOptions

    @Override
    String toString() {
        "$host-$port"
    }

}
