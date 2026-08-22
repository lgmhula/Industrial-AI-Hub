package dev.reboot.service;

import dev.reboot.entity.Site;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.SiteMapper;
import dev.reboot.mapper.UserRoleMapper;
import dev.reboot.mapper.UserSiteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SiteAccessService 授权核心逻辑测试（P1-01）。
 *
 * <p>覆盖：全局 ADMIN 放行、站点成员角色判定、403 拒绝、可访问站点解析。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class SiteAccessServiceTest {

    @Mock private UserSiteMapper userSiteMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private SiteMapper siteMapper;
    @InjectMocks private SiteAccessService siteAccessService;

    private Site site(Long id, String code) {
        Site s = new Site();
        s.setId(id);
        s.setSiteCode(code);
        s.setSiteName(code);
        return s;
    }

    /* ---- isGlobalAdmin ---- */

    @Test
    void isGlobalAdmin_shouldBeTrueForAdminRole() {
        when(userRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        assertTrue(siteAccessService.isGlobalAdmin(1L));
    }

    @Test
    void isGlobalAdmin_shouldBeFalseForNonAdmin() {
        when(userRoleMapper.findRoleCodesByUserId(2L)).thenReturn(List.of("OPERATOR"));
        assertFalse(siteAccessService.isGlobalAdmin(2L));
    }

    @Test
    void isGlobalAdmin_shouldBeFalseForNullUser() {
        assertFalse(siteAccessService.isGlobalAdmin(null));
        verify(userRoleMapper, never()).findRoleCodesByUserId(anyLong());
    }

    /* ---- accessibleSiteIds ---- */

    @Test
    void accessibleSiteIds_admin_shouldReturnNull() {
        when(userRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        assertNull(siteAccessService.accessibleSiteIds(1L));
    }

    @Test
    void accessibleSiteIds_member_shouldReturnSiteIds() {
        when(userRoleMapper.findRoleCodesByUserId(2L)).thenReturn(List.of("VIEWER"));
        when(userSiteMapper.findSiteIdsByUserId(2L)).thenReturn(List.of(10L, 20L));
        assertEquals(List.of(10L, 20L), siteAccessService.accessibleSiteIds(2L));
    }

    @Test
    void accessibleSiteIds_noMembership_shouldReturnEmpty() {
        when(userRoleMapper.findRoleCodesByUserId(3L)).thenReturn(List.of("VIEWER"));
        when(userSiteMapper.findSiteIdsByUserId(3L)).thenReturn(List.of());
        assertEquals(List.of(), siteAccessService.accessibleSiteIds(3L));
    }

    /* ---- assertSiteAccess ---- */

    @Test
    void assertSiteAccess_admin_shouldAlwaysPass() {
        when(userRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        // 即使无 user_site 成员记录，ADMIN 也放行
        assertDoesNotThrow(() -> siteAccessService.assertSiteAccess(1L, 999L, RoleEnum.VIEWER));
        verify(userSiteMapper, never()).findRoleCodeByUserAndSite(anyLong(), anyLong());
    }

    @Test
    void assertSiteAccess_memberWithEnoughRole_shouldPass() {
        when(userRoleMapper.findRoleCodesByUserId(2L)).thenReturn(List.of("VIEWER"));
        when(userSiteMapper.findRoleCodeByUserAndSite(2L, 10L)).thenReturn("OPERATOR");
        assertDoesNotThrow(() -> siteAccessService.assertSiteAccess(2L, 10L, RoleEnum.VIEWER));
        assertDoesNotThrow(() -> siteAccessService.assertSiteAccess(2L, 10L, RoleEnum.OPERATOR));
    }

    @Test
    void assertSiteAccess_memberWithInsufficientRole_shouldThrow403() {
        when(userRoleMapper.findRoleCodesByUserId(3L)).thenReturn(List.of("VIEWER"));
        when(userSiteMapper.findRoleCodeByUserAndSite(3L, 10L)).thenReturn("VIEWER");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> siteAccessService.assertSiteAccess(3L, 10L, RoleEnum.OPERATOR));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    void assertSiteAccess_nonMember_shouldThrow403() {
        when(userRoleMapper.findRoleCodesByUserId(3L)).thenReturn(List.of("VIEWER"));
        when(userSiteMapper.findRoleCodeByUserAndSite(3L, 10L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> siteAccessService.assertSiteAccess(3L, 10L, RoleEnum.VIEWER));
    }

    /* ---- defaultSiteId / listAccessibleSites ---- */

    @Test
    void defaultSiteId_shouldResolveDefault() {
        when(siteMapper.findByCode("DEFAULT")).thenReturn(site(1L, "DEFAULT"));
        assertEquals(1L, siteAccessService.defaultSiteId());
    }

    @Test
    void listAccessibleSites_admin_shouldReturnAll() {
        when(userRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(siteMapper.findAll()).thenReturn(List.of(site(1L, "DEFAULT"), site(2L, "S2")));
        assertEquals(2, siteAccessService.listAccessibleSites(1L).size());
    }

    @Test
    void listAccessibleSites_member_shouldReturnOwned() {
        when(userRoleMapper.findRoleCodesByUserId(2L)).thenReturn(List.of("VIEWER"));
        when(userSiteMapper.findSiteIdsByUserId(2L)).thenReturn(List.of(10L, 99L));
        when(siteMapper.findById(10L)).thenReturn(site(10L, "S10"));
        when(siteMapper.findById(99L)).thenReturn(null); // 站点已删则跳过
        List<Site> sites = siteAccessService.listAccessibleSites(2L);
        assertEquals(1, sites.size());
        assertEquals("S10", sites.get(0).getSiteCode());
    }
}
