CREATE TABLE `memory_fact` (
  `memoryId` bigint unsigned NOT NULL AUTO_INCREMENT,
  `userId` int unsigned NOT NULL,
  `roleId` int unsigned NOT NULL DEFAULT 0 COMMENT '0 表示所有角色',
  `namespace` varchar(32) NOT NULL DEFAULT 'profile',
  `factKey` varchar(100) NOT NULL,
  `factValue` text NOT NULL,
  `confidence` decimal(5,4) NOT NULL DEFAULT 1.0000,
  `sourceMessageId` int unsigned DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `expiresAt` datetime DEFAULT NULL,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`memoryId`),
  UNIQUE KEY `uk_memory_owner_key` (`userId`, `roleId`, `namespace`, `factKey`),
  KEY `idx_memory_owner_status` (`userId`, `roleId`, `status`, `expiresAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结构化长期记忆';

CREATE TABLE `knowledge_base` (
  `knowledgeBaseId` bigint unsigned NOT NULL AUTO_INCREMENT,
  `userId` int unsigned NOT NULL,
  `roleId` int unsigned DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `embeddingConfigId` int unsigned NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`knowledgeBaseId`),
  KEY `idx_knowledge_base_owner` (`userId`, `roleId`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人知识库';

CREATE TABLE `knowledge_document` (
  `documentId` bigint unsigned NOT NULL AUTO_INCREMENT,
  `knowledgeBaseId` bigint unsigned NOT NULL,
  `fileName` varchar(255) NOT NULL,
  `filePath` varchar(500) NOT NULL,
  `mimeType` varchar(100) NOT NULL,
  `fileSize` bigint unsigned NOT NULL,
  `sha256` char(64) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PROCESSING',
  `errorMessage` varchar(500) DEFAULT NULL,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`documentId`),
  UNIQUE KEY `uk_knowledge_document_hash` (`knowledgeBaseId`, `sha256`),
  KEY `idx_knowledge_document_status` (`knowledgeBaseId`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档';

CREATE TABLE `knowledge_chunk` (
  `chunkId` bigint unsigned NOT NULL AUTO_INCREMENT,
  `knowledgeBaseId` bigint unsigned NOT NULL,
  `documentId` bigint unsigned NOT NULL,
  `ordinalNo` int unsigned NOT NULL,
  `content` mediumtext NOT NULL,
  `tokenCount` int unsigned NOT NULL,
  `embedding` mediumtext NOT NULL COMMENT='JSON float array，个人版小规模余弦检索',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`chunkId`),
  UNIQUE KEY `uk_knowledge_chunk_ordinal` (`documentId`, `ordinalNo`),
  KEY `idx_knowledge_chunk_base` (`knowledgeBaseId`, `documentId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库切片';

CREATE TABLE `voice_profile` (
  `profileId` bigint unsigned NOT NULL AUTO_INCREMENT,
  `userId` int unsigned NOT NULL,
  `displayName` varchar(100) NOT NULL,
  `modelName` varchar(100) NOT NULL,
  `modelVersion` varchar(50) NOT NULL,
  `embeddingDimension` int unsigned NOT NULL,
  `centroidEmbedding` mediumtext NOT NULL COMMENT='JSON float array',
  `thresholdValue` decimal(6,5) NOT NULL DEFAULT 0.72000,
  `state` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`profileId`),
  KEY `idx_voice_profile_owner` (`userId`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='声纹档案';

CREATE TABLE `voice_clone` (
  `cloneId` bigint unsigned NOT NULL AUTO_INCREMENT,
  `userId` int unsigned NOT NULL,
  `configId` int unsigned NOT NULL,
  `provider` varchar(30) NOT NULL,
  `name` varchar(100) NOT NULL,
  `samplePath` varchar(500) NOT NULL,
  `sampleMimeType` varchar(100) NOT NULL,
  `sampleSha256` char(64) NOT NULL,
  `providerTaskId` varchar(255) DEFAULT NULL,
  `providerVoiceId` varchar(255) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'CREATING',
  `errorMessage` varchar(500) DEFAULT NULL,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`cloneId`),
  KEY `idx_voice_clone_owner` (`userId`, `status`),
  KEY `idx_voice_clone_provider_task` (`provider`, `providerTaskId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='克隆音色';

CREATE TABLE `message_metrics` (
  `metricId` bigint unsigned NOT NULL AUTO_INCREMENT,
  `messageId` int unsigned DEFAULT NULL,
  `userId` int unsigned DEFAULT NULL,
  `deviceId` varchar(64) NOT NULL,
  `roleId` int unsigned DEFAULT NULL,
  `sessionId` varchar(100) DEFAULT NULL,
  `statDate` date NOT NULL,
  `sttDurationMs` int unsigned DEFAULT NULL,
  `llmTtftMs` int unsigned DEFAULT NULL,
  `llmDurationMs` int unsigned DEFAULT NULL,
  `ttsFirstAudioMs` int unsigned DEFAULT NULL,
  `ttsDurationMs` int unsigned DEFAULT NULL,
  `endToEndMs` int unsigned DEFAULT NULL,
  `promptTokens` int unsigned NOT NULL DEFAULT 0,
  `completionTokens` int unsigned NOT NULL DEFAULT 0,
  `sttProvider` varchar(30) DEFAULT NULL,
  `llmProvider` varchar(30) DEFAULT NULL,
  `ttsProvider` varchar(30) DEFAULT NULL,
  `success` tinyint(1) NOT NULL DEFAULT 1,
  `errorCode` varchar(64) DEFAULT NULL,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`metricId`),
  UNIQUE KEY `uk_message_metrics_message` (`messageId`),
  KEY `idx_message_metrics_user_date` (`userId`, `statDate`),
  KEY `idx_message_metrics_device_date` (`deviceId`, `statDate`),
  KEY `idx_message_metrics_role_date` (`roleId`, `statDate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单轮对话指标';

CREATE TABLE `reminder` (
  `reminderId` bigint unsigned NOT NULL AUTO_INCREMENT,
  `userId` int unsigned NOT NULL,
  `deviceId` varchar(64) NOT NULL,
  `title` varchar(100) NOT NULL,
  `content` varchar(500) NOT NULL,
  `timezone` varchar(64) NOT NULL,
  `localTime` time NOT NULL,
  `recurrenceType` varchar(16) NOT NULL,
  `weekdays` varchar(32) DEFAULT NULL,
  `nextTriggerAt` datetime NOT NULL COMMENT='UTC DATETIME',
  `deliveryPolicy` varchar(32) NOT NULL DEFAULT 'NEXT_CONNECT',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `version` int unsigned NOT NULL DEFAULT 0,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`reminderId`),
  KEY `idx_reminder_due` (`status`, `nextTriggerAt`),
  KEY `idx_reminder_owner` (`userId`, `deviceId`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闹钟与提醒';

CREATE TABLE `reminder_delivery` (
  `deliveryId` bigint unsigned NOT NULL AUTO_INCREMENT,
  `reminderId` bigint unsigned NOT NULL,
  `scheduledAt` datetime NOT NULL COMMENT='UTC DATETIME',
  `attemptNo` int unsigned NOT NULL DEFAULT 0,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `sentAt` datetime DEFAULT NULL,
  `ackAt` datetime DEFAULT NULL,
  `errorMessage` varchar(500) DEFAULT NULL,
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`deliveryId`),
  UNIQUE KEY `uk_reminder_delivery_schedule` (`reminderId`, `scheduledAt`),
  KEY `idx_reminder_delivery_status` (`status`, `scheduledAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒投递记录';
