CREATE TABLE IF NOT EXISTS scheduled_tasks (
  task_name VARCHAR(200) NOT NULL,
  task_instance VARCHAR(200) NOT NULL,
  task_data BLOB,
  execution_time TIMESTAMP(6) NOT NULL,
  picked BOOLEAN NOT NULL,
  picked_by VARCHAR(50),
  last_success TIMESTAMP(6) NULL,
  last_failure TIMESTAMP(6) NULL,
  consecutive_failures INT,
  last_heartbeat TIMESTAMP(6) NULL,
  version BIGINT NOT NULL,
  PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX IF NOT EXISTS execution_time_idx ON scheduled_tasks(execution_time);
CREATE INDEX IF NOT EXISTS last_heartbeat_idx ON scheduled_tasks(last_heartbeat);
