package top.dteam.dgate.utils;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.jwt.JWT;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    private static final Pattern BEARER = Pattern.compile("^Bearer$", Pattern.CASE_INSENSITIVE);

    public static void fireSingleMessageResponse(HttpServerResponse response, int statusCode) {
        response.setStatusCode(statusCode).end();
    }

    public static void fireSingleMessageResponse(HttpServerResponse response, int statusCode, String message) {
        response.setStatusCode(statusCode).end(message);
    }

    public static void fireJsonResponse(HttpServerResponse response, int statusCode, Map payload) {
        response.setStatusCode(statusCode);
        JsonObject jsonObject = new JsonObject(payload);
        response.putHeader("content-type", "application/json; charset=utf-8").end(jsonObject.toString());
    }

    public static JWTAuth createAuthProvider(Vertx vertx) {
        return JWTAuth.create(vertx, new JWTAuthOptions(jwtOptions()));
    }

    // extracted from constructor of JWTAuthProviderImpl
    public static JWT createJWT(Vertx vertx) {
        JsonObject jwtOptions = jwtOptions();
        JsonObject keyStore = jwtOptions.getJsonObject("keyStore");

        try {
            KeyStore ks = KeyStore.getInstance(keyStore.getString("type", "jceks"));
            synchronized (Utils.class) {
                final Buffer keystore = vertx.fileSystem().readFileBlocking(keyStore.getString("path"));
                try (InputStream in = new ByteArrayInputStream(keystore.getBytes())) {
                    ks.load(in, keyStore.getString("password").toCharArray());
                }
            }

            return new JWT(ks, keyStore.getString("password").toCharArray());
        } catch (KeyStoreException | IOException | FileSystemException | CertificateException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonObject jwtOptions() {
        if (System.getenv("dgate_key_store") != null) {
            return new JsonObject().put("keyStore", new JsonObject()
                    .put("path", System.getenv("dgate_key_store"))
                    .put("type", System.getenv("dgate_key_type"))
                    .put("password", System.getenv("dgate_key_password")));
        } else {
            // dgate.jceks is in test/resources and for test only !!!
            return new JsonObject().put("keyStore", new JsonObject()
                    .put("path", "dgate.jceks")
                    .put("type", "jceks")
                    .put("password", "dcloud"));
        }
    }

    // extracted from JWTAuthHandlerImpl
    public static String getTokenFromHeader(HttpServerRequest request) {
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

    public static void addHandlerExcept(List<String> all, List<String> ignore, Router router, Handler handler) {
        all.removeAll(ignore);
        all.forEach(url -> router.route(url).handler(handler));
    }
}
