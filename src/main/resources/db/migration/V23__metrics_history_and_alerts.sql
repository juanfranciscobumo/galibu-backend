-- Histórico persistido de métricas del backend y eventos de alertas.
create table if not exists metrics_history (
    id bigint not null auto_increment primary key,
    captured_at bigint not null,
    requests bigint not null default 0,
    errors bigint not null default 0,
    error_rate double not null default 0,
    avg_ms double not null default 0,
    p95_ms double not null default 0,
    rps double not null default 0,
    heap_used bigint not null default 0,
    heap_max bigint not null default 0,
    db_pool_active int not null default 0,
    db_pool_total int not null default 0,
    external_requests bigint not null default 0,
    front_events bigint not null default 0,
    front_errors bigint not null default 0,
    active_subscriptions bigint not null default 0,
    mrr double not null default 0,
    open_jobs bigint not null default 0,
    jobs_without_bids bigint not null default 0,
    key idx_metrics_captured (captured_at)
) engine = InnoDB default charset = utf8mb4;

create table if not exists alert_events (
    id bigint not null auto_increment primary key,
    alert_key varchar(80) not null,
    severity varchar(20) not null,
    status varchar(20) not null,
    title varchar(160) not null,
    detail varchar(500) not null,
    metric_value double null,
    threshold_value double null,
    created_at bigint not null,
    acknowledged_at bigint null,
    key idx_alert_key (alert_key),
    key idx_alert_created (created_at)
) engine = InnoDB default charset = utf8mb4;
