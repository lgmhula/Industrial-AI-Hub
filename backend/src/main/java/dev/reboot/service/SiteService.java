package dev.reboot.service;

import dev.reboot.entity.Site;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Site 业务逻辑层（P1-01）—— 当前用户可访问站点。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Service
public class SiteService {

    private final SiteAccessService siteAccessService;

    public SiteService(SiteAccessService siteAccessService) {
        this.siteAccessService = siteAccessService;
    }

    /** 当前用户可访问的站点列表（全局管理员 = 全部站点）。 */
    public List<Site> listAccessibleSites(Long userId) {
        return siteAccessService.listAccessibleSites(userId);
    }
}
