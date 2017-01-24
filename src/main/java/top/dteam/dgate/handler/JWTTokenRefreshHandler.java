package top.dteam.dgate.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dteam.dgate.utils.JWTTokenRefresher;
import top.dteam.dgate.utils.Utils;

import java.util.HashMap;
import java.util.regex.Pattern;

public class JWTTokenRefreshHandler implements Handler<RoutingContext> {

    public static final String URL = "/token-refresh";
    private static final Logger logger = LoggerFactory.getLogger(JWTTokenRefreshHandler.class);
    private static final Pattern BEARER = Pattern.compile("^Bearer$", Pattern.CASE_INSENSITIVE);

    private JWTTokenRefresher jwtTokenRefresher;
    private long refreshLimit;
    private long refreshExpire;

    public JWTTokenRefreshHandler(JWTTokenRefresher jwtTokenRefresher, long refreshLimit, long refreshExpire) {
        this.jwtTokenRefresher = jwtTokenRefresher;
        this.refreshLimit = refreshLimit;
        this.refreshExpire = refreshExpire;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        String payload = getToken(routingContext.request());

        if (payload != null) {
            jwtTokenRefresher.setPayload(payload);
            if (jwtTokenRefresher.lessThan(refreshLimit)) {
                HashMap<String, String> tokenMap = new HashMap<>();
                tokenMap.put("token", jwtTokenRefresher.refresh(refreshExpire));
                Utils.fireJsonResponse(routingContext.response(), 200, tokenMap);
            } else {
                Utils.fireSingleMessageResponse(routingContext.response(), 401);
            }
        } else {
            Utils.fireSingleMessageResponse(routingContext.response(), 401);
        }
    }

    // extracted from JWTAuthHandlerImpl
    private String getToken(HttpServerRequest request) {
        final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);

        if (authorization != null) {
            String[] parts = authorization.split(" ");
            if (parts.length == 2) {
                final String scheme = parts[0],
                        credentials = parts[1];

                if (BEARER.matcher(scheme).matches()) {
                    return credentials;
                }
            } else {
                logger.warn("Format is Authorization: Bearer [token]");
                return null;
            }
        } else {
            logger.warn("No Authorization header was found");
            return null;
        }

        return null;
    }

}
