package top.dteam.dgate;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import top.dteam.dgate.config.ApiGatewayRepository;
import top.dteam.dgate.utils.cache.CacheLocator;
import top.dteam.dgate.utils.cache.ResponseHolder;

public class Launcher extends io.vertx.core.Launcher {

    public static void main(String[] args) {

        //Force to use slf4j
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");

        new Launcher().dispatch(args);
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
        options.setClustered(true);
    }

    @Override
    public void afterStartingVertx(Vertx vertx) {
        CacheLocator.init(vertx);
    }
}
