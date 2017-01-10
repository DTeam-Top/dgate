package top.dteam.dgate.config

import groovy.transform.CompileStatic

@CompileStatic
class LoginConfig {

    private String url
    private Map config

    LoginConfig(def login) {
        if (login instanceof String) {
            url = login
        } else if (login instanceof Map) {
            if (login.ignore && login.only) {
                throw new InvalidConfiguriationException('ignore and only both can not be in login config.')
            }

            config = (Map) login
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

}
