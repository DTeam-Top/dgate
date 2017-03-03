package top.dteam.dgate.handler;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;
import top.dteam.dgate.config.RelayTo;
import top.dteam.dgate.config.UrlConfig;
import top.dteam.dgate.utils.RequestUtils;
import top.dteam.dgate.utils.Utils;

public class RelayHandler implements GatewayRequestHandler {

    private Vertx vertx;
    private RelayTo relayTo;
    private String nameOfApiGateway;
    private RequestUtils requestUtils;

    public RelayHandler(Vertx vertx, UrlConfig urlConfig) {
        this.vertx = vertx;
        this.relayTo = urlConfig.getRelayTo();
        this.requestUtils = new RequestUtils(vertx);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        HttpClientRequest relay = requestUtils.replay(request.method(), relayTo.getHost(), relayTo.getPort(), request.uri(), simpleResponse ->
                Utils.fireJsonResponse(routingContext.response(), simpleResponse.getStatusCode(), simpleResponse.getPayload().getMap())
        );

        try {
            relay.headers().addAll(request.headers());
            Pump pump = Pump.pump(request, relay);
            request.endHandler(Void -> relay.end());
            pump.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public GatewayRequestHandler nameOfApiGateway(String nameOfApiGateway) {
        this.nameOfApiGateway = nameOfApiGateway;
        return this;
    }
}
