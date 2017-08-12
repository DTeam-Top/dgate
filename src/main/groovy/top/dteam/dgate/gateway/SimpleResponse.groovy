package top.dteam.dgate.gateway

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.vertx.core.json.JsonObject

@EqualsAndHashCode
@CompileStatic
class SimpleResponse {

    int statusCode
    JsonObject payload

    JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject()
        jsonObject.put("statusCode", statusCode).put("payload", payload)

        jsonObject
    }

}
