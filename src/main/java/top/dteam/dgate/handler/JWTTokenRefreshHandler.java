package top.dteam.dgate.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import top.dteam.dgate.utils.JWTTokenRefresher;
import top.dteam.dgate.utils.Utils;

import java.util.HashMap;

public class JWTTokenRefreshHandler implements Handler<RoutingContext> {

    public static final String URL = "/token-refresh";

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
        String payload = Utils.getTokenFromHeader(routingContext.request());

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
}
