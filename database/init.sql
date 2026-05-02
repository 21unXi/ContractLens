-- ContractLens Database Initialization
-- Last updated: 2026-05-02

CREATE DATABASE IF NOT EXISTS `contractlens` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `contractlens`;

-- 1. 用户表 (users)
CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 合同表 (contracts)
CREATE TABLE IF NOT EXISTS `contracts` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '关联用户ID',
  `title` VARCHAR(255) NOT NULL COMMENT '合同标题',
  `contract_type` VARCHAR(20) DEFAULT 'rental' COMMENT '合同类型',
  `content` TEXT NOT NULL COMMENT '合同原文',
  `content_hash` VARCHAR(64) DEFAULT NULL COMMENT '合同内容hash',
  `file_type` VARCHAR(100) NOT NULL COMMENT '文件类型（txt/docx/pdf）',
  `file_path` VARCHAR(500) DEFAULT NULL COMMENT '原始文件存储路径',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_contracts_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同表';

-- 3. 分析结果表 (analysis_results)
CREATE TABLE IF NOT EXISTS `analysis_results` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `contract_id` BIGINT NOT NULL COMMENT '关联合同ID',
  `contract_content_hash` VARCHAR(64) DEFAULT NULL COMMENT '分析基于的合同内容hash',
  `risk_level` VARCHAR(10) NOT NULL COMMENT '风险等级（高/中/低）',
  `risk_score` INT DEFAULT NULL COMMENT '风险评分（0-100）',
  `summary` TEXT COMMENT '合同一句话摘要',
  `party_lessor_risks` JSON DEFAULT NULL COMMENT '房东视角风险',
  `party_tenant_risks` JSON DEFAULT NULL COMMENT '租客视角风险',
  `suggestions` JSON DEFAULT NULL COMMENT '修改建议',
  `contract_tags` JSON DEFAULT NULL COMMENT '合同标签',
  `retrieved_context` TEXT COMMENT '向量检索结果',
  `graph_context` TEXT COMMENT '图谱检索结果',
  `clause_conflicts` JSON DEFAULT NULL COMMENT '发现的条款冲突',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_id` (`contract_id`),
  CONSTRAINT `fk_analysis_results_contract_id` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分析结果表';

-- 4. 分析对话消息表 (analysis_chat_messages)
CREATE TABLE IF NOT EXISTS `analysis_chat_messages` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `contract_id` BIGINT NOT NULL COMMENT '关联合同ID',
  `role` VARCHAR(20) NOT NULL COMMENT '角色（user/assistant）',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_chat_contract_created` (`contract_id`, `created_at`),
  CONSTRAINT `fk_analysis_chat_messages_contract_id` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分析对话消息表';

-- 5. 知识库文档表 (knowledge_docs)
CREATE TABLE IF NOT EXISTS `knowledge_docs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `doc_id` VARCHAR(50) NOT NULL COMMENT '文档唯一ID',
  `title` VARCHAR(255) NOT NULL COMMENT '文档标题',
  `doc_type` VARCHAR(20) NOT NULL COMMENT '文档类型（law/case/guide/risk）',
  `content` TEXT NOT NULL COMMENT '文档正文',
  `law_article` VARCHAR(100) DEFAULT NULL COMMENT '关联法条',
  `risk_type` VARCHAR(50) DEFAULT NULL COMMENT '风险类型',
  `chunk_count` INT DEFAULT NULL COMMENT '被切分的块数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';
