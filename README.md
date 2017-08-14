# dgate：an API Gateway based on Vert.x

dgate是基于Vertx的API Gateway。运行dgate的命令如下：

~~~
java -jar dgate-version-fat.jar -Dconf=conf
~~~

其中，conf中定义了路由规则，下面是一个简单的例子：

~~~
apiGateway {
    port = 7000
    host = 'localhost'
    urls {
        "/url1" {
            required = ['param1', 'param2']
            methods = ['GET', 'POST']
            upstreamURLs = [
                [host: 'localhost', port: 8080, url: '/test']
            ]
        }
        "/url2" {
            required = ['param1', 'param2']
            methods = ['GET', 'POST']
            upstreamURLs = [
                [host: 'localhost', port: 8080, url: '/test1'],
                [host: 'localhost', port: 8080, url: '/test2']
            ]
        }
    }
}
~~~

dgate的主要特性：
- 轻量级配置，无需后端DB
- DSL为groovy语法
- 支持mock
- 支持url的转发和组合（即一个外部url对应后端多个url），并支持before（向后端发送请求前）和after（收到后端全部响应后）闭包。
- 支持request透传：form和upload用这种模式。
- 支持URL Path Parameters
- 支持JWT
- 支持CORS
- 灵活的login配置（此时，在启动时需要先设置环境变量，请参见手册）
- 支持断路器
- 灵活的请求缓存策略
- 支持集群

详细的用户指南请访问[这里](./docs/user_guide.md)。

## 开发指南

- git clone
- gradle shadowJar，生成dgate的fatjar
- gradle test，运行测试代码

在发起Pull Request时，请同时提交测试代码，并保证现有测试代码【对于测试，我们推荐[Spock](http://spockframework.org/)】能全部通过，;)。
