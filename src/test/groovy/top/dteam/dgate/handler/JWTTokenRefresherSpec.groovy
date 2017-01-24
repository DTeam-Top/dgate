package top.dteam.dgate.handler

import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import spock.lang.Specification
import top.dteam.dgate.config.ApiGatewayRepository
import top.dteam.dgate.gateway.ApiGateway
import top.dteam.dgate.gateway.SimpleResponse
import top.dteam.dgate.utils.RequestUtils
import top.dteam.dgate.utils.TestUtils

class JWTTokenRefresherSpec extends Specification {

    private final static long TIME_OUT = 5

    Vertx vertx
    RequestUtils requestUtils

    String config = """
        import io.vertx.core.http.HttpMethod
        import io.vertx.core.Vertx
        import io.vertx.ext.auth.jwt.JWTAuth
        import top.dteam.dgate.utils.*

        apiGateway {
            port = 7000
            login {
                url = "/login"
                refreshLimit = ${TIME_OUT}
                refreshExpire = ${TIME_OUT}
            }
            urls {
                "/login" {
                    expected {
                        statusCode = 200
                        payload = {
                            JWTAuth jwtAuth = Utils.createAuthProvider(Vertx.vertx())
                            JWTTokenGenerator tokenGenerator = new JWTTokenGenerator(jwtAuth)
                            [token: tokenGenerator.token(["sub": "13572209183", "name": "foxgem", "role": "normal"], ${
        TIME_OUT
    })]
                        }
                    }
                }
                "/proxy" {
                    expected {
                        statusCode = 200
                        payload = [test: 'true']
                    }
                }
            }
        }
    """

    void setup() {
        vertx = Vertx.vertx()
        deployGate()
        requestUtils = new RequestUtils(vertx)
    }

    void cleanup() {
        vertx.close()
    }

    def "Should get 401 if refreshing happens before login"() {
        setup:
        SimpleResponse result

        when:
        sleep(100)
        requestUtils.get("localhost", 7000, JWTTokenRefreshHandler.URL, new JsonObject()) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 401
    }

    def "An expired token which is activity before refreshLimit seconds could refresh successfully"() {
        setup:
        String oldToken
        String newToken
        SimpleResponse resultAfterRefreshing
        SimpleResponse resultAfterTokenRefreshedExpired

        when:
        sleep(100)
        requestUtils.get("localhost", 7000, '/login', new JsonObject()) { simpleResponse ->
            oldToken = simpleResponse.payload.getString('token')
        }
        TestUtils.waitResult(oldToken, 1500)

        sleep(TIME_OUT * 1000)

        requestUtils.requestWithJwtToken(HttpMethod.POST, "localhost", 7000, JWTTokenRefreshHandler.URL,
                new JsonObject(), oldToken) { simpleResponse ->
            newToken = simpleResponse.payload.getString('token')
        }
        TestUtils.waitResult(newToken, 1500)

        requestUtils.requestWithJwtToken(HttpMethod.POST, "localhost", 7000, '/proxy',
                new JsonObject(), newToken) { simpleResponse ->
            resultAfterRefreshing = simpleResponse
        }
        TestUtils.waitResult(resultAfterRefreshing, 1500)

        sleep(TIME_OUT * 1000)

        requestUtils.requestWithJwtToken(HttpMethod.POST, "localhost", 7000, '/proxy',
                new JsonObject(), newToken) { simpleResponse ->
            resultAfterTokenRefreshedExpired = simpleResponse
        }
        TestUtils.waitResult(resultAfterTokenRefreshedExpired, 1500)

        then:
        oldToken != newToken
        resultAfterRefreshing.statusCode == 200
        resultAfterRefreshing.payload.map == [test: 'true']
        resultAfterTokenRefreshedExpired.statusCode == 401
    }

    def "A very old expired token could not refresh successfully"() {
        setup:
        SimpleResponse result
        String oldToken

        when:
        sleep(100)
        requestUtils.get("localhost", 7000, '/login', new JsonObject()) { simpleResponse ->
            oldToken = simpleResponse.payload.getString('token')
        }
        TestUtils.waitResult(oldToken, 1500)

        sleep(TIME_OUT * 2 * 1000 + 500)

        requestUtils.requestWithJwtToken(HttpMethod.POST, "localhost", 7000, JWTTokenRefreshHandler.URL,
                new JsonObject(), oldToken) { simpleResponse ->
            result = simpleResponse
        }
        TestUtils.waitResult(result, 1500)

        then:
        result.statusCode == 401
    }

    private void deployGate() {
        vertx.deployVerticle(new ApiGateway(ApiGatewayRepository.build(config)[0]))
    }

}
