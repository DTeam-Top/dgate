package top.dteam.dgate;

public class Launcher extends io.vertx.core.Launcher {

    public static void main(String[] args) {
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");

        new Launcher().dispatch(args);
    }

}
