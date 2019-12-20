package top.dteam.dgate.config

import groovy.transform.CompileStatic

@CompileStatic
class LoginConfig {

    static long DEFAULT_REFRESH_LIMIT = 30 * 60
    static int DEFAULT_REFRESH_EXPIRE = 30 * 60

    private String url
    private Map config

    LoginConfig(def login) {
        if (login instanceof String) {
            url = login
        } else if (login instanceof Map) {
            if (login.ignore && login.only) {
                throw new InvalidConfiguriationException('ignore and only both can not be in login config.')
            }

            config = login as Map
        } else {
            throw new InvalidConfiguriationException('login could be a String or a Map only.')
        }
    }

    String login() {
        if (url) {
            url
        } else {
            config?.url
        }
    }

    List<String> ignore() {
        (List<String>) (config?.ignore ?: [])
    }

    List<String> only() {
        (List<String>) (config?.only ?: [])
    }

    long refreshLimit() {
        (long) (config?.refreshLimit ?: DEFAULT_REFRESH_LIMIT)
    }

    int refreshExpire() {
        (int) (config?.refreshExpire ?: DEFAULT_REFRESH_EXPIRE)
    }

}
