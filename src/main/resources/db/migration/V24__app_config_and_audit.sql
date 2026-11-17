-- Configuración de la aplicación y auditoría de cambios.
create table if not exists app_config (
    config_key varchar(120) not null primary key,
    config_value varchar(500) not null,
    value_type varchar(20) not null default 'STRING',
    description varchar(255) null,
    updated_at bigint not null,
    updated_by varchar(80) not null default 'system'
) engine = InnoDB default charset = utf8mb4;

create table if not exists app_config_audit (
    id bigint not null auto_increment primary key,
    config_key varchar(120) not null,
    old_value varchar(500) null,
    new_value varchar(500) not null,
    changed_at bigint not null,
    changed_by varchar(80) not null,
    key idx_config_audit_key (config_key),
    key idx_config_audit_changed (changed_at)
) engine = InnoDB default charset = utf8mb4;
