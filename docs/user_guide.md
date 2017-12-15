# dgate：an API Gateway based on Vert.x

dgate是基于Vertx的API Gateway。运行dgate的命令如下：

~~~
java -jar dgate-version-fat.jar -Dconf=conf
~~~

其中的conf属性用来指定运行所需的配置文件或文件夹，如果conf属性为文件夹，则dgate加载该文件夹内后缀为`.conf`的文件作为配置文件，每个文件必须是可独立加载的配置文件。（若配置文件中包含有login配置，在启动时需要先设置环境变量，请参见JWT部分。）

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
    expires // 全局缓存过期时间，单位毫秒。默认0，即不缓存
    required //必需参数列表
    methods  //支持的HTTP Method
    upstreamURLs { 上游URL列表（UpStreamURL） }
    expected //期望返回值
    relayTo //透传请求到后端
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

其中的**tokenGenerator**就是利用这个特性完成的，具体实现可以参见**LoginHandler**。注意，上述代码中的2表示token的超时时间，单位为秒。

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

## 透传请求（Relay请求）

有时，期望前端的请求只是从dgate穿堂而过，不做任何修改，如下面两类请求：
- FORM表单
- FileUpload

这就是relay请求发挥作用的地方，在实际中，凡事注明是relayTo的url，dgate会将源request完整不动的发到后端，包括它的request header。它的配置如下：

~~~
"/relayAnotherURL" {
    relayTo {
        host = 'localhost'
        port = 8081
    }
}
~~~

对于relay请求，不需要指定后端url，只需指定主机名和端口即可。这也正是透传的含义：除了位置不同，其余都一样，这样也可以方便老旧程序快速和dgate集成。但需注意：relay请求不支持before/after闭包，也不支持required和method配置。

## 安全

dgate支持JWT Token来认证每个访问层请求，当配置中出现login时，安全机制即被触发。

### Login的配置

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

### JWT Token

dgate使用JWT Token来认证每个访问层请求，故每个请求必需先请求获得jwt token，然后将每个token放入后续每个request的"Authorization"头，这样每个后续的request才会被认为是有效请求，否则将返回401。

如何产生jwt token的职责由开发者来完成，它必需放入**login**配置部分，上面的例子给出了一个参考实现。

对于发往后端服务的每个请求，dgate会将前端请求Authorization头中的jwt token附到请求的参数内，可以通过**token**来获取，此时它已经被解码成一个JSON对象。

产生JWT Token的密钥由下面的三个环境变量决定，故一旦配置中包含login，则需要在启动dgate之前先设置这3个环境变量：
- dgate_key_store，keystore文件路径
- dgate_key_type，keystore文件类型
- dgate_key_password，keystore的密钥，至少6位

下面是一个配置的例子

~~~
export dgate_key_store=./test1.jceks
export dgate_key_type=jceks
export dgate_key_password=test123
~~~

使用keytool来产生文件，例子如下：

~~~
keytool -genseckey -keystore test1.jceks -storetype jceks -storepass test123 -keyalg HMacSHA256 -keysize 2048 -alias HS256 -keypass test123
~~~

由于dgate利用Vert.x的JWTAuthProvider产生JWT Token，因此其密钥文件为符合其要求的文件格式。关于密钥和如何产生密钥文件的命令，可以参见[其文档](http://vertx.io/docs/vertx-auth-jwt/java/)。

注意：
- 在产品环境中请安全地保管好这个密钥文件！
- 在开发环境可以使用dgate自带的测试密钥文件：[dgate](../src/test/resources/dgate.jceks)，密码为“dcloud”。请注意不要将其用于产品环境，否则为伪造证书提供了便利。

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

注意：不要忘记payload本身是一个**闭包**！否则，无法模拟JWT Token过期后重新生成另一个Token的情况。

### 刷新JWT Token

dgate支持JWT Token的刷新，刷新用的url为：/token-refresh。它会从Request Header中取出JWT，解码，然后重新生成新的JWT。即，刷新JWT Token的前提是必须先获得dgate的JWT Token。

刷新JWT Token的配置由login配置块决定。当login是一个url时，则使用缺省的刷新属性。刷新属性（单位：秒）包括：
- refreshLimit，刷新时限，即对于一个超时的JWT Token，若距离当前时间小于这个值，则允许刷新。否则返回401。
- refreshExpire，新生成的JWT Token的时限，一旦超过，则对应的JWT Token失效。

若要自定义这两个值，则可按照如下定义：

~~~
login {
    ……
    refreshLimit = 30 * 60
    refreshExpire = 30 * 60
    ……
}
~~~

若不指定，则使用缺省值。这两个值的缺省值都为：30分钟，即：30 * 60（秒）。

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

> **NOTE**：在产品环境中不要使用“\*”作为allowedOriginPattern的值。

## 每个请求的附加参数

dgate会给每个发往后端服务的请求头中添加若干参数，通过下面的key可以获得：
- dgate-gateway，Api Gateway的名字，字符串。
- dgate-jwt-token，已解码的jwt token json字符串，内容依赖于在产生token时设置的值。如：在产生时包含[sub, name, role]这几个键值，则此处就获得这3个键值。若在产生时为[sub, name, role, other]，则此处就可以会有这4个键值。只要请求头中携带有dgate签发的jwt，每个发往后端服务的请求参数中就会有这个参数。

注意：除了必需的几个属性，JWT Token中token本身是可以附加其他属性进来的。相当于将token本身作为信息的载体。

这些头都是以BASE64编码放入的，故取出时需要用BASE64解码。

## 断路器设置

dgate缺省会为每个上游服务（注：对于Mock服务，断路器设置无效）设置一个断路器，缺省的配置如下：
- 最大失败次数（maxFailures），3次
- 请求超时（timeout），5秒
- 断路器重置时间（resetTimeout），10秒

若缺省的设置不合适，dgate支持两个层次的设置：gateway级别和上游服务级别。并且，在两者都存在时，上游服务的断路器设置会覆盖gateway级别的设置。

### gateway级别的设置

这个设置将对gateway内所有的上游服务生效，例子如下：

~~~
apiGateway {
    ……
    circuitBreaker {
        maxFailures = 5
        timeout = 10000
        resetTimeout = 30000
    }
    ……
}
~~~

### 上游服务级别的设置

这个设置仅对当前上游服务生效，例子如下：

~~~
……
"/url" {
    upstreamURLs = [
        [host: 'localhost', port: 8080, url: '/test1',
         circuitBreaker: [maxFailures: 2, timeout: 3000, resetTimeout: 3000]]
    ]
}
……
~~~

## 缓存设置
dgate支持URL缓存，可以将反向代理的后端服务返回的响应缓存一段时间，减少后端服务的压力。可以在全局和局部配置中使用`expires`指令设置缓存过期时间，单位**毫秒**。全局默认`expires = 0`，即所有URL不启用缓存，所有请求由反向代理的后端直接响应。

缓存策略仅对**成功**的返回(HTTP状态码200)生效，这也是情理之中的。

一个典型的缓存配置参考如下:
~~~
apiGateway1 {
    port = 7000
    expires = 10000  // apiGateWay1下所有URL缓存10秒
    urls {
        "/url1" {
            expires = 20000  // "/url1"的请求缓存20秒
            required = ["sub", "password"]
            methods = [HttpMethod.GET, HttpMethod.POST]
            upstreamURLs = [
                [
                    host: 'localhost', port: 8080, url: '/url1',
                    expires: 0  // 对此upstreamURL不启用缓存
                ],
                [
                    host: 'localhost', port: 8081, url: '/url1'
                ]  // 对此upstreamURL继承/url1的缓存配置，即20秒
            ]
        }
        "/url2" {
            expires = 15000  // 对于url2透传的后端，结果缓存15秒
            relayTo {
                host = 'localhost'
                port = 8081
            }
        }
        "/url3" {
            /** SNIP **/
            // 此URL的缓存配置沿用全局缓存配置，即10秒
        }
    }
}
~~~

可配置`expires`的位置:
- `apiGateway`，对该apiGateway下所有的URL配置统一的缓存过期时间
- `url`，仅对此url规则中的所有upstreamURL或relayTo配置缓存策略。
- `upstreamURLs`，仅对个别upstreamURL配置缓存策略
- `expires = 0`表示此级别不启用缓存。

> **NOTE**: JWT Token会影响缓存结果，不同的token获取到缓存内容会不同。

> **NOTE**: 由于每个url的缓存过期时间可能不一样。因此，dgate的缓存内部实现是每个url单独一个cacheName，每个cacheName最大缓存条目1000，防止被恶意扫描导致缓存占满内存。

## 日志级别
默认情况下，dgate本身的日志将以`DEBUG`级别输出，其他第三方类库将以`WARN`级别输出。可以通过设置`DGATE_LOG_LEVEL`这个`System property`或环境变量覆盖这个默认值。

例如:

~~~bash
# 以下两种方式等效,如果同时设置，则system property优先使用
java -DDGATE_LOG_LEVEL=WARN -jar dgate-0.1-fat.jar
DGATE_LOG_LEVEL=WARN java -jar dgate-0.1-fat.jar
~~~

## 集群
默认情况下，dgate以单节点模式运行。如果希望让dgate以跨主机集群模式运行，需要指定环境变量`DGATE_CLUSTER_NODES`，如`192.168.1.2,192.168.1.3`，相关文档参考[ignite集群配置](https://apacheignite.readme.io/docs/cluster-config#static-ip-based-discovery)。

> **NOTE**: 以集群方式运行dgate，需要所有dgate实例配置相同的`DGATE_CLUSTER_NODES`环境变量。

Example:
~~~bash
export DGATE_CLUSTER_NODES=192.168.1.2,192.168.1.3   # 集群ip以逗号分割
java -jar dgate-0.1-fat.jar -Dconf=/path/to/config.conf
~~~

## Mock EventBusBridge
为了简化前端和后端基于Vert.x Web EventBusBridge交互的调试，dgate提供了对这种交互方式的模拟。对应的DSL例子如下：

~~~
apiGateway {
    port = 7001
    host = 'localhost'

    eventBusBridge {
        urlPattern ='/eventbus/*'
        publishers {
            'target_address' {
                expected = {
                    [timestamp: Instant.now()]
                }
                timer = 1000
            }
        }
        consumers {
            'consumer_address' {
                target = "target_address"
                expected = [test: true] // 或者 {message -> ...}
            }
        }
    }
}
~~~

语法很简单，与Mock HTTP几乎一致。其中：
- publishers，对应后端主动发起的推送，对于每一个推送地址，timer必填，单位为毫秒。
- consumers，对应后端接收前端消息的消费者。
  - 若target不写，则对应的模式为：message.reply
  - 若给出target，则对应eventbus.publish

对于expected，它即可以为一个固定的值，也可以为一个闭包。当为闭包时，其返回值为mock结果。同时，对于consumers中的expected，闭包的入参为event message。