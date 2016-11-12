package top.dteam.dgate.gateway

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.vertx.core.json.JsonObject;

@EqualsAndHashCode
@CompileStatic
public class SimpleResponse {

    int statusCode
    JsonObject payload

    JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject()
        jsonObject.put("statusCode", statusCode)
        jsonObject.put("payload", payload)

        jsonObject
    }

}
