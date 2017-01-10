package top.dteam.dgate.config

import spock.lang.Specification
import spock.lang.Unroll

class LoginConfigSpec extends Specification {

    def "should work for String"() {
        when:
        LoginConfig loginConfig = new LoginConfig('/login')

        then:
        loginConfig.login() == '/login'
        !loginConfig.ignore()
        !loginConfig.only()
    }

    @Unroll
    def "should work for Map: #config"() {
        when:
        LoginConfig loginConfig = new LoginConfig(config)

        then:
        loginConfig.login() == config.url
        loginConfig.ignore() == (config.ignore ?: [])
        loginConfig.only() == (config.only ?: [])

        where:
        config << [
                [url: '/login1', ignore: ['/url1', '/url2']],
                [url: '/login1', only: ['/url3']]
        ]
    }

    def "should throw InvalidConfiguriationException when ignore and only are both in config block"() {
        when:
        new LoginConfig([url: '/login1', ignore: ['/url1'], only: ['/url2']])

        then:
        thrown(InvalidConfiguriationException)
    }

}
