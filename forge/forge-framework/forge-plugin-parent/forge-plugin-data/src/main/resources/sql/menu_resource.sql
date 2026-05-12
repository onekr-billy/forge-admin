-- 菜单资源：数据资产管理
-- resource_type: 1=目录，2=菜单，3=按钮，4=API接口
INSERT INTO sys_resource (id, tenant_id, resource_name, parent_id, resource_type, sort, `path`, component, is_external,
                          is_public, menu_status, visible, perms, icon, api_method, api_url, keep_alive, always_show,
                          redirect, remark, create_by, create_time, update_by, update_time, create_dept, client_code)
VALUES (9100, 1, '数据资产', 0, 1, 600, '/data', NULL, 0, 0, 1, 1, NULL,
        'DataAnalysisOutlined', NULL, NULL, 0, 1, NULL, '数据资产管理目录', 1,
        NOW(), 1, NOW(), 1, 'pc'),
       (9101, 1, '数据连接', 9100, 2, 1, '/data/connection', 'data/connection', 0, 0, 1, 1, 'data:connection:list',
        'DatabaseOutlined', NULL, NULL, 1, 0, NULL, '数据连接管理菜单', 1,
        NOW(), 1, NOW(), 1, 'pc'),
       (9102, 1, '数据集管理', 9100, 2, 2, '/data/dataset', 'data/dataset', 0, 0, 1, 1, 'data:dataset:list',
        'TableOutlined', NULL, NULL, 1, 0, NULL, '数据集管理菜单', 1,
        NOW(), 1, NOW(), 1, 'pc');

-- 按钮资源：数据连接管理
INSERT INTO sys_resource (id, tenant_id, resource_name, parent_id, resource_type, sort, `path`, component, is_external,
                          is_public, menu_status, visible, perms, icon, api_method, api_url, keep_alive, always_show,
                          redirect, remark, create_by, create_time, update_by, update_time, create_dept, client_code)
VALUES (9103, 1, '查询数据连接列表', 9101, 3, 1, NULL, NULL, 0, 0, 1, 1, 'data:connection:query', NULL, NULL, NULL, 0, 0, NULL,
        '数据连接查询按钮', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9104, 1, '新增数据连接', 9101, 3, 2, NULL, NULL, 0, 0, 1, 1, 'data:connection:add', NULL, NULL, NULL, 0, 0, NULL,
        '数据连接新增按钮', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9105, 1, '修改数据连接', 9101, 3, 3, NULL, NULL, 0, 0, 1, 1, 'data:connection:edit', NULL, NULL, NULL, 0, 0, NULL,
        '数据连接修改按钮', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9106, 1, '删除数据连接', 9101, 3, 4, NULL, NULL, 0, 0, 1, 1, 'data:connection:remove', NULL, NULL, NULL, 0, 0, NULL,
        '数据连接删除按钮', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9107, 1, '测试数据连接', 9101, 3, 5, NULL, NULL, 0, 0, 1, 1, 'data:connection:test', NULL, NULL, NULL, 0, 0, NULL,
        '数据连接测试按钮', 1, NOW(), 1, NOW(), 1, 'pc');

-- 按钮资源：数据集管理
INSERT INTO sys_resource (id, tenant_id, resource_name, parent_id, resource_type, sort, `path`, component, is_external,
                          is_public, menu_status, visible, perms, icon, api_method, api_url, keep_alive, always_show,
                          redirect, remark, create_by, create_time, update_by, update_time, create_dept, client_code)
VALUES (9108, 1, '查询数据集列表', 9102, 3, 1, NULL, NULL, 0, 0, 1, 1, 'data:dataset:query', NULL, NULL, NULL, 0, 0, NULL,
        '数据集查询按钮', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9109, 1, '新增数据集', 9102, 3, 2, NULL, NULL, 0, 0, 1, 1, 'data:dataset:add', NULL, NULL, NULL, 0, 0, NULL,
        '数据集新增按钮', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9110, 1, '修改数据集', 9102, 3, 3, NULL, NULL, 0, 0, 1, 1, 'data:dataset:edit', NULL, NULL, NULL, 0, 0, NULL,
        '数据集修改按钮', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9111, 1, '删除数据集', 9102, 3, 4, NULL, NULL, 0, 0, 1, 1, 'data:dataset:remove', NULL, NULL, NULL, 0, 0, NULL,
        '数据集删除按钮', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9112, 1, '预览数据集', 9102, 3, 5, NULL, NULL, 0, 0, 1, 1, 'data:dataset:preview', NULL, NULL, NULL, 0, 0, NULL,
        '数据集预览按钮', 1, NOW(), 1, NOW(), 1, 'pc');

-- API资源：数据连接管理
INSERT INTO sys_resource (id, tenant_id, resource_name, parent_id, resource_type, sort, `path`, component, is_external,
                          is_public, menu_status, visible, perms, icon, api_method, api_url, keep_alive, always_show,
                          redirect, remark, create_by, create_time, update_by, update_time, create_dept, client_code)
VALUES (9113, 1, '数据连接分页查询API', 9101, 4, 1, NULL, NULL, 0, 0, 1, 1, 'data:connection:page', NULL, 'GET', '/data/connection/page', 0, 0, NULL,
        '数据连接分页查询接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9114, 1, '数据连接列表API', 9101, 4, 2, NULL, NULL, 0, 0, 1, 1, 'data:connection:list', NULL, 'GET', '/data/connection/list', 0, 0, NULL,
        '数据连接列表接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9115, 1, '数据连接详情API', 9101, 4, 3, NULL, NULL, 0, 0, 1, 1, 'data:connection:detail', NULL, 'GET', '/data/connection/:id', 0, 0, NULL,
        '数据连接详情接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9116, 1, '数据连接新增API', 9101, 4, 4, NULL, NULL, 0, 0, 1, 1, 'data:connection:add', NULL, 'POST', '/data/connection', 0, 0, NULL,
        '数据连接新增接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9117, 1, '数据连接修改API', 9101, 4, 5, NULL, NULL, 0, 0, 1, 1, 'data:connection:edit', NULL, 'PUT', '/data/connection', 0, 0, NULL,
        '数据连接修改接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9118, 1, '数据连接删除API', 9101, 4, 6, NULL, NULL, 0, 0, 1, 1, 'data:connection:remove', NULL, 'DELETE', '/data/connection/:id', 0, 0, NULL,
        '数据连接删除接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9119, 1, '数据连接测试API', 9101, 4, 7, NULL, NULL, 0, 0, 1, 1, 'data:connection:test', NULL, 'POST', '/data/connection/:id/test', 0, 0, NULL,
        '数据连接测试接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9120, 1, '数据连接临时测试API', 9101, 4, 8, NULL, NULL, 0, 0, 1, 1, 'data:connection:test-temp', NULL, 'POST', '/data/connection/test', 0, 0, NULL,
        '数据连接临时测试接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9121, 1, '数据连接表列表API', 9101, 4, 9, NULL, NULL, 0, 0, 1, 1, 'data:connection:tables', NULL, 'GET', '/data/connection/:id/tables', 0, 0, NULL,
        '数据连接表列表接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9122, 1, '数据连接字段列表API', 9101, 4, 10, NULL, NULL, 0, 0, 1, 1, 'data:connection:fields', NULL, 'GET', '/data/connection/:id/tables/:tableName/fields', 0, 0, NULL,
        '数据连接字段列表接口', 1, NOW(), 1, NOW(), 1, 'pc');

-- API资源：数据集管理
INSERT INTO sys_resource (id, tenant_id, resource_name, parent_id, resource_type, sort, `path`, component, is_external,
                          is_public, menu_status, visible, perms, icon, api_method, api_url, keep_alive, always_show,
                          redirect, remark, create_by, create_time, update_by, update_time, create_dept, client_code)
VALUES (9123, 1, '数据集分页查询API', 9102, 4, 1, NULL, NULL, 0, 0, 1, 1, 'data:dataset:page', NULL, 'GET', '/data/dataset/page', 0, 0, NULL,
        '数据集分页查询接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9124, 1, '数据集列表API', 9102, 4, 2, NULL, NULL, 0, 0, 1, 1, 'data:dataset:list', NULL, 'GET', '/data/dataset/list', 0, 0, NULL,
        '数据集列表接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9125, 1, '数据集详情API', 9102, 4, 3, NULL, NULL, 0, 0, 1, 1, 'data:dataset:detail', NULL, 'GET', '/data/dataset/:id', 0, 0, NULL,
        '数据集详情接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9126, 1, '数据集新增API', 9102, 4, 4, NULL, NULL, 0, 0, 1, 1, 'data:dataset:add', NULL, 'POST', '/data/dataset', 0, 0, NULL,
        '数据集新增接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9127, 1, '数据集修改API', 9102, 4, 5, NULL, NULL, 0, 0, 1, 1, 'data:dataset:edit', NULL, 'PUT', '/data/dataset', 0, 0, NULL,
        '数据集修改接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9128, 1, '数据集删除API', 9102, 4, 6, NULL, NULL, 0, 0, 1, 1, 'data:dataset:remove', NULL, 'DELETE', '/data/dataset/:id', 0, 0, NULL,
        '数据集删除接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9129, 1, '数据集同步字段API', 9102, 4, 7, NULL, NULL, 0, 0, 1, 1, 'data:dataset:sync-fields', NULL, 'POST', '/data/dataset/:id/sync-fields', 0, 0, NULL,
        '数据集同步字段接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9130, 1, '数据集字段保存API', 9102, 4, 8, NULL, NULL, 0, 0, 1, 1, 'data:dataset:save-fields', NULL, 'PUT', '/data/dataset/:id/fields', 0, 0, NULL,
        '数据集字段保存接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9131, 1, '数据集预览API', 9102, 4, 9, NULL, NULL, 0, 0, 1, 1, 'data:dataset:preview', NULL, 'POST', '/data/dataset/:id/preview', 0, 0, NULL,
        '数据集预览接口', 1, NOW(), 1, NOW(), 1, 'pc');

-- API资源：数据集运行时查询
INSERT INTO sys_resource (id, tenant_id, resource_name, parent_id, resource_type, sort, `path`, component, is_external,
                          is_public, menu_status, visible, perms, icon, api_method, api_url, keep_alive, always_show,
                          redirect, remark, create_by, create_time, update_by, update_time, create_dept, client_code)
VALUES (9132, 1, '数据集运行时查询API', 9102, 4, 10, NULL, NULL, 0, 0, 1, 1, 'data:dataset:runtime-query', NULL, 'POST', '/data/dataset/runtime/query', 0, 0, NULL,
        '数据集运行时查询接口', 1, NOW(), 1, NOW(), 1, 'pc'),
       (9133, 1, '数据集元数据API', 9102, 4, 11, NULL, NULL, 0, 0, 1, 1, 'data:dataset:metadata', NULL, 'GET', '/data/dataset/runtime/:id/metadata', 0, 0, NULL,
        '数据集元数据接口', 1, NOW(), 1, NOW(), 1, 'pc');

-- 角色授权：超级管理员(role_id=1)默认拥有数据资产权限
INSERT INTO sys_role_resource (id, tenant_id, role_id, resource_id, create_time)
VALUES (9100, 1, 1, 9100, NOW()),
       (9101, 1, 1, 9101, NOW()),
       (9102, 1, 1, 9102, NOW()),
       (9103, 1, 1, 9103, NOW()),
       (9104, 1, 1, 9104, NOW()),
       (9105, 1, 1, 9105, NOW()),
       (9106, 1, 1, 9106, NOW()),
       (9107, 1, 1, 9107, NOW()),
       (9108, 1, 1, 9108, NOW()),
       (9109, 1, 1, 9109, NOW()),
       (9110, 1, 1, 9110, NOW()),
       (9111, 1, 1, 9111, NOW()),
       (9112, 1, 1, 9112, NOW()),
       (9113, 1, 1, 9113, NOW()),
       (9114, 1, 1, 9114, NOW()),
       (9115, 1, 1, 9115, NOW()),
       (9116, 1, 1, 9116, NOW()),
       (9117, 1, 1, 9117, NOW()),
       (9118, 1, 1, 9118, NOW()),
       (9119, 1, 1, 9119, NOW()),
       (9120, 1, 1, 9120, NOW()),
       (9121, 1, 1, 9121, NOW()),
       (9122, 1, 1, 9122, NOW()),
       (9123, 1, 1, 9123, NOW()),
       (9124, 1, 1, 9124, NOW()),
       (9125, 1, 1, 9125, NOW()),
       (9126, 1, 1, 9126, NOW()),
       (9127, 1, 1, 9127, NOW()),
       (9128, 1, 1, 9128, NOW()),
       (9129, 1, 1, 9129, NOW()),
       (9130, 1, 1, 9130, NOW()),
       (9131, 1, 1, 9131, NOW()),
       (9132, 1, 1, 9132, NOW()),
       (9133, 1, 1, 9133, NOW());