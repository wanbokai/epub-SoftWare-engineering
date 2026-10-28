package com.reader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性（对应 application.yml 中 jwt.* 段）。
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 访问令牌有效期（毫秒），默认 2 小时 */
    private long accessTokenTtl = 7200000L;

    /** 刷新令牌有效期（毫秒），默认 14 天 */
    private long refreshTokenTtl = 1209600000L;

    /** 签名密钥 */
    private String secret;

    public long getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(long accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public long getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(long refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
