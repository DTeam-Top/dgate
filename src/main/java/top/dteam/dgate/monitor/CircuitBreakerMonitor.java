package top.dteam.dgate.monitor;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircuitBreakerMonitor extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerMonitor.class);

    private MessageConsumer consumer;

    @Override
    public void start() {
        consumer = vertx.eventBus().consumer("vertx.circuit-breaker", message -> {
            JsonObject body = (JsonObject) message.body();
            logger.info("~~~~~ Circuit Breaker Status: node={}, name={}, state={}, failures={} ~~~~~\n",
                    body.getString("node"), body.getString("name"), body.getString("state"), body.getInteger("failures"));
        });
    }

    @Override
    public void stop() {
        consumer.unregister(); //若不注销,则当用ctrl-c停止应用时,会报异常
    }

}
