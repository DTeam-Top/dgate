package top.dteam.dgate.handler;

import io.vertx.core.Handler;
import io.vertx.ext.auth.jwt.impl.JWT;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dteam.dgate.utils.Utils;

public class JWTTokenSniffer implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(JWTTokenSniffer.class);

    private JWT jwt;

    public JWTTokenSniffer(JWT jwt) {
        this.jwt = jwt;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        String payload = Utils.getTokenFromHeader(routingContext.request());

        if (payload != null) {
            try {
                routingContext.put("token", jwt.decode(payload));
            } catch (RuntimeException e) {
                logger.error(e.getMessage());
            }
        }

        routingContext.next();
    }


}
