CREATE TABLE `firmware_release` (
  `releaseId` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '固件发布ID',
  `boardType` varchar(100) NOT NULL COMMENT '固件board.type，必须与设备上报完全一致',
  `version` varchar(32) NOT NULL COMMENT '固件兼容的数字点分版本',
  `filePath` varchar(255) NOT NULL COMMENT '相对于固件根目录的文件路径',
  `fileSize` bigint unsigned NOT NULL COMMENT '文件字节数',
  `sha256` char(64) NOT NULL COMMENT '文件SHA-256',
  `forceUpdate` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否强制升级',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否允许OTA下发和下载',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`releaseId`),
  UNIQUE KEY `uk_firmware_board_version` (`boardType`, `version`),
  KEY `idx_firmware_board_enabled_created` (`boardType`, `enabled`, `createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本地固件发布表';

INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
VALUES (NULL, '固件管理', 'system:firmware', 'menu', '/firmware', 'page/Firmware', 'cloud-upload', 4, '1', '1');

INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT permissionId, '查询固件', 'system:firmware:api:list', 'api', NULL, NULL, NULL, 1, '0', '1'
FROM `sys_permission` WHERE `permissionKey` = 'system:firmware';

INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT permissionId, '发布固件', 'system:firmware:api:create', 'api', NULL, NULL, NULL, 2, '0', '1'
FROM `sys_permission` WHERE `permissionKey` = 'system:firmware';

INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT permissionId, '更新固件', 'system:firmware:api:update', 'api', NULL, NULL, NULL, 3, '0', '1'
FROM `sys_permission` WHERE `permissionKey` = 'system:firmware';

INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT permissionId, '删除固件', 'system:firmware:api:delete', 'api', NULL, NULL, NULL, 4, '0', '1'
FROM `sys_permission` WHERE `permissionKey` = 'system:firmware';

INSERT INTO `sys_auth_role_permission` (`authRoleId`, `permissionId`)
SELECT 1, permissionId FROM `sys_permission`
WHERE `permissionKey` IN (
  'system:firmware', 'system:firmware:api:list', 'system:firmware:api:create',
  'system:firmware:api:update', 'system:firmware:api:delete'
)
AND permissionId NOT IN (
  SELECT permissionId FROM `sys_auth_role_permission` WHERE `authRoleId` = 1
);
