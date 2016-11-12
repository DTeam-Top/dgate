# dgate路线图

注：本路线图是纯粹的乱想集，仅为记录一些想法，一旦实现或觉得不合适，就会从中剔除，故读者请勿严肃对待。

## 支持多种安全机制

~~~
security {
    mechanism = 'JWT'
    login = xxx
}
~~~

这样即可支持多种安全机制，如未来的OAuth2

## 支持多种发现机制

- direct，类似目前的直接链接，用ip
- cluster，vertx目前的默认方式
- consul，consul集成

## 支持多类型后端

后端服务未必全部都是RESTful service，还可以是其他类型，如eventbus端点

## 黑白名单

支持黑名单和白名单

## Reactive编程模型，rxjava

考虑采用rx编程模型进一步提高性能，当然，前后需要比较一下。目前大致如下（单request，与直接发往后台service对比）：
- CompletableFuture.allOf + CompletableFuture.whenCompleted，+200ms
- 遍历每个CompletableFuture + Atomic变量，+400ms
