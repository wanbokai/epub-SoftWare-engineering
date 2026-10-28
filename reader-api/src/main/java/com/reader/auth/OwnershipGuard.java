package com.reader.auth;

import com.reader.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 资源归属校验：确保当前登录用户只能访问自己的资源，防水平越权。
 */
@Component
public class OwnershipGuard {

    /**
     * 获取当前登录用户 ID，未登录则抛出异常。
     */
    public Long requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return userId;
    }

    /**
     * 校验资源归属，越权则抛出 403。
     *
     * @param resourceOwnerId 资源所属用户 ID
     */
    public void assertOwnership(Long resourceOwnerId) {
        Long currentUserId = requireCurrentUserId();
        if (!currentUserId.equals(resourceOwnerId)) {
            throw new BusinessException(403, "无权访问该资源");
        }
    }
}
