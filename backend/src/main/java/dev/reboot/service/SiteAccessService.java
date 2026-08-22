package dev.reboot.service;

import dev.reboot.entity.Site;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.SiteMapper;
import dev.reboot.mapper.UserRoleMapper;
import dev.reboot.mapper.UserSiteMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 站点资源访问控制核心（P1-01 Horizontal Authorization）。
 *
 * <p>授权规则（资源 = device/alarm/device_data，归属 = 设备所在 site）：</p>
 * <ol>
 *   <li>全局 ADMIN（user_role 含 ADMIN）→ 所有站点放行（系统管理员）；</li>
 *   <li>普通用户 → 依 user_site(user_id, site_id).role_id 的站点内角色，
 *       按 {@link RoleEnum#isAtLeast} 判定；无成员记录 → 403；</li>
 *   <li>列表查询用 {@link #accessibleSiteIds}：null=全部（管理员）、空=无权限、否则按站点过滤。</li>
 * </ol>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Service
public class SiteAccessService {

    private final UserSiteMapper userSiteMapper;
    private final UserRoleMapper userRoleMapper;
    private final SiteMapper siteMapper;

    public SiteAccessService(UserSiteMapper userSiteMapper,
                             UserRoleMapper userRoleMapper,
                             SiteMapper siteMapper) {
        this.userSiteMapper = userSiteMapper;
        this.userRoleMapper = userRoleMapper;
        this.siteMapper = siteMapper;
    }

    /** 是否全局系统管理员（user_role 含 ADMIN）。 */
    public boolean isGlobalAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        List<String> roles = userRoleMapper.findRoleCodesByUserId(userId);
        return roles != null && roles.contains(RoleEnum.ADMIN.getRoleCode());
    }

    /**
     * 用户可访问站点 id 列表。
     *
     * @return null = 全部站点（全局管理员）；空列表 = 无任何站点访问权；否则为成员站点列表
     */
    public List<Long> accessibleSiteIds(Long userId) {
        if (isGlobalAdmin(userId)) {
            return null;
        }
        List<Long> ids = userSiteMapper.findSiteIdsByUserId(userId);
        return ids == null ? List.of() : ids;
    }

    /**
     * 单对象访问断言：全局 ADMIN 放行；否则要求该站点 user_site 角色 ≥ required。
     *
     * @throws BusinessException 403 无站点访问权
     */
    public void assertSiteAccess(Long userId, Long siteId, RoleEnum required) {
        if (isGlobalAdmin(userId)) {
            return;
        }
        String code = (siteId == null) ? null : userSiteMapper.findRoleCodeByUserAndSite(userId, siteId);
        RoleEnum siteRole = RoleEnum.fromCode(code);
        if (siteRole == null || !siteRole.isAtLeast(required)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源");
        }
    }

    /** 默认站点 id（site_code=DEFAULT；不存在返回 null）。 */
    public Long defaultSiteId() {
        Site site = siteMapper.findByCode("DEFAULT");
        return site == null ? null : site.getId();
    }

    /** 当前用户可访问站点列表（管理员 = 全部站点）。 */
    public List<Site> listAccessibleSites(Long userId) {
        List<Long> ids = accessibleSiteIds(userId);
        if (ids == null) {
            return siteMapper.findAll();
        }
        return ids.stream().map(siteMapper::findById).filter(Objects::nonNull).toList();
    }
}
