package dev.reboot.service;

import dev.reboot.dto.RoleDTO;
import dev.reboot.dto.RoleVO;
import dev.reboot.entity.Role;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.RoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色管理业务逻辑层 — 角色 CRUD + 启用/禁用。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Service
public class RoleService {

    private static final List<String> PROTECTED_ROLE_CODES = List.of("ADMIN", "OPERATOR", "VIEWER");

    private final RoleMapper roleMapper;

    public RoleService(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    public List<RoleVO> findAll() {
        return roleMapper.findAll().stream().map(RoleVO::from).toList();
    }

    public RoleVO findById(Long id) {
        Role role = roleMapper.findById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return RoleVO.from(role);
    }

    public RoleVO create(RoleDTO dto) {
        if (roleMapper.findByCode(dto.getRoleCode()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码已存在");
        }
        Role role = new Role();
        role.setRoleName(dto.getRoleName());
        role.setRoleCode(dto.getRoleCode());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        roleMapper.insert(role);
        return RoleVO.from(role);
    }

    public RoleVO update(Long id, RoleDTO dto) {
        Role role = roleMapper.findById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        role.setRoleName(dto.getRoleName());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus() != null ? dto.getStatus() : role.getStatus());
        roleMapper.update(role);
        return RoleVO.from(role);
    }

    public void delete(Long id) {
        Role role = roleMapper.findById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        if (PROTECTED_ROLE_CODES.contains(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内置角色不允许删除");
        }
        roleMapper.softDeleteById(id);
    }

    public void toggleStatus(Long id) {
        Role role = roleMapper.findById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        if (PROTECTED_ROLE_CODES.contains(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内置角色不允许禁用");
        }
        int newStatus = role.getStatus() == 1 ? 0 : 1;
        roleMapper.updateStatus(id, newStatus);
    }
}
