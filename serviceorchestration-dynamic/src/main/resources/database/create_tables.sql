USE `ah_serviceorchestration_dynamic`;

-- Logs

CREATE TABLE IF NOT EXISTS `logs` (
  `log_id` varchar(100) NOT NULL,
  `entry_date` timestamp(3) NULL DEFAULT NULL,
  `logger` varchar(100) DEFAULT NULL,
  `log_level` varchar(100) DEFAULT NULL,
  `message` mediumtext,
  `exception` mediumtext,
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;  

-- Subscription

CREATE TABLE IF NOT EXISTS `subscription` (
  `id` binary(16) NOT NULL,
  `owner_system` varchar(63) NOT NULL,
  `target_system` varchar(63) NOT NULL,
  `service_definition` varchar(63) NOT NULL,
  `expires_at` timestamp,
  `notify_protocol` varchar(14) NOT NULL,
  `notify_properties` mediumtext NOT NULL,
  `orchestration_request` mediumtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `subscription_uk` (`owner_system`, `target_system`, `service_definition`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8; 

-- Orchestration job

CREATE TABLE IF NOT EXISTS `orchestration_job` (
  `id` binary(16) NOT NULL,
  `status` varchar(14) NOT NULL,
  `type` varchar(14) NOT NULL,
  `requester_system` varchar(63) NOT NULL,
  `target_system` varchar(63) NOT NULL,
  `service_definition` varchar(63) NOT NULL,
  `subscription_id` varchar(63),
  `message` mediumtext,
  `created_at` timestamp NOT NULL,
  `started_at` timestamp NOT NULL,
  `finished_at` timestamp NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8; 

-- Orchestration lock

CREATE TABLE IF NOT EXISTS `orchestration_lock` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `orchestration_job_id` varchar(63) NOT NULL,
  `service_instance_id` varchar(255) NOT NULL,
  `owner` varchar(63) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `temporary` int(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;