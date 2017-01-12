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

在发起Pull Request时，请同时提交测试代码，并保证现有测试代码【对于测试，我们推荐[Spock](http://spockframework.org/)】能全部通过，;)。