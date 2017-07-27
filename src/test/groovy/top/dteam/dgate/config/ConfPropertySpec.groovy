package top.dteam.dgate.config

import spock.lang.Specification

class ConfPropertySpec extends Specification {
    def "conf property should be working with single file"() {
        when:
        ApiGatewayRepository.load("src/test/resources/config/conf2")

        then:
        ApiGatewayRepository.respository.size() == 1
        with(ApiGatewayRepository.respository[0]) {
            port == 7002
            host == '0.0.0.0'
        }
    }

    def "conf property should be working with single directory"() {
        when:
        ApiGatewayRepository.load("src/test/resources/config")

        then:
        ApiGatewayRepository.respository.size() == 2
        with(ApiGatewayRepository.respository[0]) {
            port == 7001
            host == '0.0.0.0'
        }
        with(ApiGatewayRepository.respository[1]) {
            port == 7003
            host == '0.0.0.0'
        }
    }

    def "conf property should throw NoSuchFileException if no such file or directory"() {
        when:
        ApiGatewayRepository.load("/no/such/file/or/directory")

        then:
        thrown(FileNotFoundException)
    }

}
