# dgate：an API Gateway based on Vert.x

dgate是基于Vertx的API Gateway。运行dgate的命令如下：

~~~
java -jar dgate-version-fat.jar -Dconf=conf
~~~

其中的conf属性用来指定运行所需的配置文件。

## conf的文件格式

在说明格式之前，先看一个例子：

~~~
apiGateway1 {
    port = 7000
    login = "/login"
    urls {
        "/login" {
            required = ["sub", "password"]
            methods = [HttpMethod.GET, HttpMethod.POST]
            upstreamURLs = [
                [
                    host: 'localhost', port: 8080, url: '/login',
                    after: { simpleResponse ->
                        Map payload = [
                            sub: simpleResponse.payload.getString("sub"),
                            name: simpleResponse.payload.getString("name"),
                            role: simpleResponse.payload.getString("role")
                        ]
                        simpleResponse.payload.put('token', delegate.tokenGenerator.token(payload, 5))
                        simpleResponse
                    }
                ]
            ]
        }
        "/summary" {
            expected {
                statusCode = 200
                payload {
                    eqLocations = []
                    opRateInLast30Days = []
                    myOrgs = [
                        ["name": "org1", "admin": false]
                    ]
                }
            }
        }
        "/forward" {
            required = ['param1', 'param2']
            methods = ['GET', 'POST']
            upstreamURLs = [
                [host: 'localhost', port: 8080, url: '/test']
            ]
        }
        "/composite" {
            required = ['param1', 'param2']
            methods = ['GET', 'POST']
            upstreamURLs = [
                [host: 'localhost', port: 8080, url: '/test1'],
                [host: 'localhost', port: 8080, url: '/test2']
            ]
        }
    }
}
apiGateway2 {
    port = 7001
    host = 'localhost'
    urls {
        "/mock" {
            expected {
                statusCode = 200
                payload = [test: true]
            }
        }
    }
}
~~~

dgate采用[ConfigSlurper](http://docs.groovy-lang.org/latest/html/gapi/groovy/util/ConfigSlurper.html)解析conf文件，因此其文件的语法实际上是Groovy语法。

conf文件由多个Api Gateway的定义组成，对于每个Gateway定义如下：

~~~
apiGatewayName {
    port //端口
    host //绑定的ip或主机名,默认0.0.0.0
    login //后端login服务的配置
    urls { URL配置（UrlConfig）列表 } //dgate暴露的url列表
}
~~~

对于每个URL配置，其结构如下：

~~~
url {
    required //必需参数列表
    methods  //支持的HTTP Method
    upstreamURLs { 上游URL列表（UpStreamURL） }
    expected //期望返回值
}
~~~

其中：
- required支持两种格式：List和Map。前者适用于不区分HttpMethod时的参数验证，后者则可以针对不同的HttpMethod分别进行设置。
  - List，如：required = ["sub", "password"]
  - Map，如：required = [get: ['param1'], post: ['param2'], delete: ['param3']]
- expected和upStreamURLs两个属性不能并存
- expected主要用于mock模式，其目的是为了便于依赖dgate的访问层可以自行mock所需的响应，使得它们的开发进度受dgate开发进度的影响最小化。
- 对于expected的内容：
  - statusCode和payload至少有一个
  - 或针对具体的HTTP METHOD的返回值
- 对于每个上游URL，有3个属性：host、port和url，其中url必需以"/"开始

## 不同类型的API Gateway请求

API Gateway的目的是作为后端Service的一个统一集中点，简化访问层与后端Service的交互。说得更简单点，其作用非常类似Facade。

因此，API Gateway的主要作用就是：
- 接受访问层的请求
- 将请求转发到对应的后端服务
- 收集后端响应，统一组装成response，然后返回给访问层

从大的方面讲，发给API Gateway的请求分成两类：
- 转发给一个上游URL，即上面的forward
- 转发给多个上游URL，即上面的Composite

这便是为何upStreamURLs是一个列表的主要原因。对于每种请求：
- forward
  - 访问层的request parameter直接转发给后端service
  - 后端service的response直接转发给访问层
- composite
  - 访问层的request parameter会直接（并发）转发给每个后端服务
  - 后端服务的response会merge在一起后再发给访问层

对于每个发往后端的请求，dgate会采用Circuit Breaker来防护，防止出现由于某个后端service的失效导致整个系统雪崩。同时，为了简单起见，dgate和后端service之间只通过JSON进行交互：
- 对于发往dgate的请求，dgate会将：request parameters、form变量、request body合并为一体，统一作为request body发往后端
- 对于后端响应，dgate只接受json格式

## UpStreamURL的扩展点

如上节所说，API Gateway具备两个职责：转发请求和转发响应。由于每个后端服务所需的request参数和产生的response不同，在这两个阶段，都需要对访问层传来的request和后端服务传回的response进行定制：
- 需要将访问层的request适配成后端service所需的request，这就面临着一些格式转换，如参数改名、增减对应的参数等。
- 需要对后端服务的response适配成访问层所需的response，采用类似的格式转换的动作。

针对这个需求，每个UpStreamURL都有两个可选属性可以利用。

### before闭包

~~~
upstreamURLs = [
    [
        host: 'localhost', port: 8080, url: '/test',
        before: { params -> ... }
    ]
]
~~~

before闭包的参数为访问层所发来的request parameters，其类型是一个**JsonObject**。开发者可以通过调用相关的方法来对其内容进行增减，dgate会将before闭包的返回值作为参数发给对应的上游URL。故，在此处非常适合将多余的request params过滤掉。

请注意：before闭包的返回值必需是**JsonObject**。最简单的before闭包如下：

~~~
before: { params -> params }
~~~

### after闭包

~~~
upstreamURLs = [
    [
        host: 'localhost', port: 8080, url: '/test',
        after: { simpleResponse -> ... }
    ]
]
~~~

after闭包的参数为后端服务所发来的response,其类型是**SimpleResponse**。开发者可以利用相关方法来自定义其内容，dgate会将after闭包的返回值作为最终结果与其他后端服务的响应合并，然后返回给访问层。故，此处适合对响应进行自定义。

请注意：after闭包的返回值必需是**SimpleResponse**。最简单的after闭包如下：

~~~
after: { simpleResponse -> simpleResponse }
~~~

### 给before/after闭包传入工具类

由于Groovy闭包的特性，我们可以给before/after传入工具类实例，供开发者使用。典型的例子如下：

~~~
"/login" {
    required = ["sub", "password"]
    methods = [HttpMethod.GET, HttpMethod.POST]
    upstreamURLs = [
        [
            host: 'localhost', port: 8080, url: '/login',
            after: { simpleResponse ->
                simpleResponse.put(tokenGenerator.token(["sub": "13572209183", "name": "foxgem",
                                                         "role": "normal"], 2))
                simpleResponse
            }
        ]
    ]
}
~~~

其中的**tokenGenerator**就是利用这个特性完成的，具体实现可以参见**LoginHandler**。

### url path parameters

UpStreamURL除了支持一般的url格式，还支持url path parameters，格式如下：
- '/:x'
- '/y/:x?'
- '/:x?/test/:y?'

所有的参数以":"开始，以"?"结束的为可选参数，规定如下：
- 当url中有且仅有一个可选参数时，它必需为最后一个参数
- 当url中有多个可选参数时，则只能从后向前不出现在url中，即：对于/:x?/:y?/:z?
  - /x/y/z，合法
  - /x/y，合法
  - /x，合法
  - /x/z，非法，它将解析对应成：/x/y


## Mock请求

最简单的mock请求的书写如下：

~~~
"/mock" {
    expected {
        statusCode = 200
        payload = [test: true]
    }
}
~~~

若statusCode或payload本身是一个闭包，则dgate会将它们的返回值作为结果返回，这在excepted为一个动态值时非常有用，参见下面【Mock JWT Token】的例子。

上面的形式对于任何Http Method不加区分地返回同一个内容。但在Restful URL中，往往会有一个URL要为不同的Http Method服务，执行不同动作（如CRUD），返回不同响应体的情形。在dgate中可以以如下形式书写：

~~~
"/mock" {
    expected {
        get {
            statusCode = 200
            payload = [method: 'get']
        }
        post {
            statusCode = 200
            payload = [method: 'post']
        }
        delete {
            statusCode = 200
            payload = [method: 'delete']
        }
    }
}
~~~

即对于每一种HTTP method，都有各自响应体，每个响应体的格式都是由statusCode和payload组成。

## JWT Token

dgate使用JWT Token来认证每个访问层请求，故每个请求必需先请求获得jwt token，然后将每个token放入后续每个request的"Authorization"头，这样每个后续的request才会被认为是有效请求，否则将返回401。

如何产生jwt token的职责由开发者来完成，它必需放入**login**配置部分，上面的例子给出了一个参考实现。

对于发往后端服务的每个请求，dgate会将前端请求Authorization头中的jwt token附到请求的参数内，可以通过**token**来获取。

### Mock JWT Token

对于使用Mock功能来进行开发的访问层，有时会需要mock login来获得mock的jwt token以便开发相应的功能。可以参考下面的代码：

~~~
import io.vertx.core.http.HttpMethod
import io.vertx.core.Vertx
import io.vertx.ext.auth.jwt.JWTAuth
import top.dteam.dgate.utils.*

apiGateway {
    port = 7000
    login = "/login"
    urls {
        "/login" {
            required = ["sub", "password"]
            methods = [HttpMethod.GET, HttpMethod.POST]
            expected {
                statusCode = 200
                payload = {
                    JWTAuth jwtAuth = Utils.createAuthProvider(Vertx.vertx())
                    JWTTokenGenerator tokenGenerator = new JWTTokenGenerator(jwtAuth)
                    [token: tokenGenerator.token(["sub": "13572209183", "name": "foxgem", "role": "normal"], 200)]
                }
            }
        }
    ……
}
~~~

既然login指向的是dgate暴露的url，那么当然也就是可以直接mock啦。

注意：不要忘记payload本身是一个**闭包**！

## CORS支持

对于某些情况下，需要打开CORS支持才能让访问层正常使用。在dgate中需要在apiGateway中添加如下的配置：

~~~
apiGateway {
    ……
    cors {
        allowedOriginPattern = "*"
        allowedMethods = [HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE]
        allowedHeaders = ['content-type']
        allowCredentials = true
    }
    ……
}
~~~

cors配置并非必需的，若没有，dgate不会支持CORS。CORS的配置项目如下：
- allowedOriginPattern，字符串，如 “http://localhost:7000”。
- allowedHeaders，字符串集合，支持的HTTP请求HEADER集合
- allowedMethods，Vertx的HttpMethod集合，支持的HTTP方法集合
- exposedHeaders，字符串集合，允许暴露的响应头集合
- maxAgeSeconds，整型，可用于缓存preflight的时间，单位（秒）
- allowCredentials，是否允许Cookie

其中仅allowedOriginPattern是必填。但，对于提交格式为JSON的ajax请求，需要允许：'content-type'。

此外，在调试过程中，也可以注意浏览器给出的提示，根据相应提示进行配置。

警告：在产品环境中不要使用“\*”作为allowedOriginPattern的值。

## 每个请求的附加参数

dgate会给每个发往后端服务的请求参数中添加若干参数，通过下面的key可以获得：
- nameOfApiGateway，Api Gateway的名字，字符串。
- token，已解码的jwt token，其类型是一个Map，内容依赖于在产生token时设置的值。如：在产生时包含[sub, name, role]这几个键值，则此处就获得这3个键值。若在产生时为[sub, name, role, other]，则此处就可以会有这4个键值。

注意：除了必需的几个属性，JWT Token中token本身是可以附加其他属性进来的。相当于将token本身作为信息的载体。

## Login的配置

若conf中没有login配置，则所有url都是公共可访问的。

若要对所有url都要求先登录，则配置如下：

~~~
login = "/login"
~~~

若要忽略某些url，则配置如下：

~~~
login {
    url = "/login"
    ignore = [ 被忽略的url ]
}
~~~

若只有部分url是需要访问控制的，则配置如下：

~~~
login {
    url = "/login"
    only = [ 被控制的url ]
}
~~~

注意：ignore和only不能同时存在。
