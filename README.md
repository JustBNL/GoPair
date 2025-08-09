# GoPair 微服务架构项目

## 项目概述

GoPair是一个基于Spring Boot和Spring Cloud的微服务架构项目，采用多模块Maven结构，实现了用户认证、API网关等核心功能。

## 项目架构

### 模块结构

```
gopair-parent/                    # 父项目（Parent POM）
├── gopair-common/               # 公共工具模块
├── gopair-gateway/              # API网关模块
└── gopair-user-service/         # 用户服务模块
```

### 各模块说明

#### gopair-common（公共工具模块）
- **功能**: 提供公共的工具类、常量、枚举、异常等
- **包含内容**:
  - 常量定义（MessageConstants）
  - 枚举类（ErrorCode、IErrorCode）
  - 核心响应类（R）
  - 工具类（JwtUtils）
  - 异常类（BusinessException）

#### gopair-gateway（API网关模块）
- **端口**: 8080
- **功能**: 
  - 请求路由和负载均衡
  - JWT认证和授权
  - 请求过滤和转发
- **核心组件**:
  - GatewayApplication：网关启动类
  - AuthGlobalFilter：全局认证过滤器

#### gopair-user-service（用户服务模块）
- **端口**: 8081
- **功能**: 
  - 用户注册、登录
  - 用户信息管理
  - 用户认证
- **核心组件**:
  - UserApplication：用户服务启动类
  - AuthController：认证控制器
  - UserService：用户服务
  - SecurityConfig：安全配置

## 技术栈

- **Spring Boot**: 3.5.3
- **Spring Cloud**: 2023.0.0
- **Spring Security**: 安全框架
- **MyBatis Plus**: ORM框架
- **MySQL**: 数据库
- **Redis**: 缓存
- **JWT**: 认证令牌
- **Knife4j**: API文档

## 快速开始

### 环境要求
- JDK 21
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 启动步骤

1. **启动数据库和Redis**
   ```bash
   # 启动MySQL
   # 启动Redis
   ```

2. **编译项目**
   ```bash
   mvn clean install
   ```

3. **启动服务**
   ```bash
   # 启动用户服务
   cd gopair-user-service
   mvn spring-boot:run
   
   # 启动网关服务
   cd gopair-gateway
   mvn spring-boot:run
   ```

### 访问地址

- **网关**: http://localhost:8080
- **用户服务**: http://localhost:8081
- **API文档**: http://localhost:8081/doc.html

## API接口

### 认证接口

#### 用户登录
- **URL**: `POST /api/auth/login`
- **请求体**:
  ```json
  {
    "username": "admin",
    "password": "123456"
  }
  ```

#### 用户注册
- **URL**: `POST /api/auth/register`
- **请求体**:
  ```json
  {
    "username": "newuser",
    "password": "123456",
    "email": "user@example.com"
  }
  ```

#### 获取用户信息
- **URL**: `GET /api/auth/info`
- **认证**: 需要Bearer Token

## 配置说明

### 数据库配置
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/gopair
spring.datasource.username=root
spring.datasource.password=123456
```

### JWT配置
```properties
gopair.jwt.secret=gopair-secret-key-2024
gopair.jwt.expiration=86400000
```

### Redis配置
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0
```

## 开发说明

### 添加新服务
1. 在根目录创建新的服务模块
2. 在父POM中添加模块声明
3. 配置服务依赖和启动类
4. 在网关中配置路由规则

### 认证流程
1. 客户端发送登录请求到网关
2. 网关转发到用户服务
3. 用户服务验证成功后返回JWT
4. 后续请求携带JWT通过网关认证

## 注意事项

1. 确保所有服务的JWT密钥配置一致
2. 网关的白名单路径配置要准确
3. 数据库连接配置要正确
4. Redis服务要正常运行

## 许可证

本项目采用MIT许可证。 