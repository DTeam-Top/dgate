import ch.qos.logback.classic.Level
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy

appender("Console", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    }
}

appender("R", RollingFileAppender) {
    file = "dgate.log"
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    }
    rollingPolicy(SizeAndTimeBasedRollingPolicy) {
        fileNamePattern = "dgate_%d{yyyy-MM-dd}.%i.log"
        maxFileSize = "10MB"
        maxHistory = 7
        totalSizeCap = "500MB"
    }
}

logger("io.vertx", Level.WARN)
logger("io.netty", Level.WARN)
logger("ch.qos.logback", Level.WARN)

final String DGATE_LOG_LEVEL = System.getProperty("DGATE_LOG_LEVEL") ?:
        System.getenv("DGATE_LOG_LEVEL")

root(Level.valueOf(DGATE_LOG_LEVEL), ["Console", "R"])
