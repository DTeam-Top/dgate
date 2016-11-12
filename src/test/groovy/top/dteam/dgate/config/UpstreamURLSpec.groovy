package top.dteam.dgate.config

import io.vertx.core.json.JsonObject
import spock.lang.Specification
import spock.lang.Unroll

class UpstreamURLSpec extends Specification {

    @Unroll
    def "对于无参数的url应该返回原值: #url"() {
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
    def "对于必填参数应该能工作: #url"() {
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
    def "若缺少必填参数应该抛异常"() {
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
    def "对于可选参数应该能工作: #url (#context)"() {
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
    def "若可选参数没有且不是最后一个应该抛异常: #url (#context)"() {
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
