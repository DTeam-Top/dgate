package top.dteam.dgate.config

import io.vertx.core.json.JsonObject
import spock.lang.Specification
import spock.lang.Unroll

class UpstreamURLSpec extends Specification {

    @Unroll
    def "should return itself for a url without path params: #url"() {
        setup:
        UpstreamURL upstreamURL = new UpstreamURL(host: 'localhost', port: 8080, url: url)

        when:
        String result = upstreamURL.resolve(context)

        then:
        result == finalUrl

        where:
        url    | context                            | finalUrl
        '/x'   | new JsonObject([x: 'test'])        | '/x'
        '/x/y' | new JsonObject([x: 'x1', y: 'y1']) | '/x/y'
    }

    @Unroll
    def "url could include required path params: #url"() {
        setup:
        UpstreamURL upstreamURL = new UpstreamURL(host: 'localhost', port: 8080, url: url)

        when:
        String result = upstreamURL.resolve(context)

        then:
        result == finalUrl

        where:
        url           | context                            | finalUrl
        '/:x'         | new JsonObject([x: 'x1'])          | '/x1'
        '/y/:x'       | new JsonObject([x: 'x1'])          | '/y/x1'
        '/:x/:y'      | new JsonObject([x: 'x1', y: 'y1']) | '/x1/y1'
        '/:x/test/:y' | new JsonObject([x: 'x1', y: 'y1']) | '/x1/test/y1'
    }

    @Unroll
    def "should throw an exception if required path params not exist"() {
        setup:
        UpstreamURL upstreamURL = new UpstreamURL(host: 'localhost', port: 8080, url: url)

        when:
        upstreamURL.resolve(context)

        then:
        thrown(InvalidConfiguriationException)

        where:
        url      | context
        '/:x'    | new JsonObject([:])
        '/:x/:y' | new JsonObject([y: 'y1'])
        '/:x/:y' | new JsonObject([x: 'x1'])
        '/:x/:y' | new JsonObject([:])
    }

    @Unroll
    def "url could include optional path params: #url (#context)"() {
        setup:
        UpstreamURL upstreamURL = new UpstreamURL(host: 'localhost', port: 8080, url: url)

        when:
        String result = upstreamURL.resolve(context)

        then:
        result == finalUrl

        where:
        url             | context                            | finalUrl
        '/:x?'          | new JsonObject([x: 'x1'])          | '/x1'
        '/:x?'          | new JsonObject([:])                | '/'
        '/y/:x?'        | new JsonObject([x: 'x1'])          | '/y/x1'
        '/y/:x?'        | new JsonObject([:])                | '/y'
        '/:x?/:y?'      | new JsonObject([x: 'x1', y: 'y1']) | '/x1/y1'
        '/:x?/:y?'      | new JsonObject([x: 'x1'])          | '/x1'
        '/:x?/:y'       | new JsonObject([x: 'x1', y: 'y1']) | '/x1/y1'
        '/:x?/test/:y?' | new JsonObject([x: 'x1'])          | '/x1/test'
        '/:x?/:y?'      | new JsonObject([:])                | '/'
        '/:x?/:y?/:z?'  | new JsonObject([:])                | '/'
    }

    @Unroll
    def "should throw an exception if missed optional path params are not the last ones in a url: #url (#context)"() {
        setup:
        UpstreamURL upstreamURL = new UpstreamURL(host: 'localhost', port: 8080, url: url)

        when:
        upstreamURL.resolve(context)

        then:
        thrown(InvalidConfiguriationException)

        where:
        url             | context
        '/:x?/:y?'      | new JsonObject([y: 'y1'])
        '/:x?/test/:y?' | new JsonObject([y: 'y1'])
        '/:x?/:y'       | new JsonObject([y: 'y1'])
        '/:x?/:y'       | new JsonObject([:])
    }

}
