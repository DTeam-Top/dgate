# dgate：an API Gateway based on Vert.x

dgate是基于Vertx的API Gateway。运行dgate的命令如下：

~~~
java -jar dgate-version-fat.jar -Dconf=conf
~~~

详细的用户指南请访问[这里](./docs/user_guide.md)。

## 开发指南

- git clone
- gradle shadowJar，生成dgate的fatjar
- gradle test，运行测试代码