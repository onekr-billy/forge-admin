package com.mdframe.forge.starter.orm.interceptor;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * 分页拦截器兼容实现。
 *
 * <p>MyBatis-Plus 默认生成 {@code COUNT(*)}。在包含较深嵌套条件的查询中，
 * 项目使用的 JSqlParser 可能无法解析该 count SQL（错误通常定位到
 * {@code SELECT COUNT(} 的左括号）。{@code COUNT(1)} 语义相同，且可被当前
 * 租户和数据权限拦截器稳定解析。</p>
 */
public class CountOnePaginationInnerInterceptor extends PaginationInnerInterceptor {

    @Override
    public String autoCountSql(IPage<?> page, String sql) {
        String countSql = super.autoCountSql(page, sql);
        return replaceCountStar(countSql);
    }

    private String replaceCountStar(String countSql) {
        if (countSql == null || countSql.isEmpty()) {
            return countSql;
        }
        String prefix = "SELECT COUNT(*)";
        if (countSql.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return "SELECT COUNT(1)" + countSql.substring(prefix.length());
        }
        return countSql;
    }
}
