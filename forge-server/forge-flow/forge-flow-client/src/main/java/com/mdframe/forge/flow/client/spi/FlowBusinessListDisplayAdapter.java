package com.mdframe.forge.flow.client.spi;

import java.util.List;

/**
 * 流程列表业务展示扩展点。
 *
 * <p>流程插件只知道流程任务和抄送记录。业务侧按 {@code businessType} 读取
 * {@code businessParams}，回填对象名、摘要和 {@code displayExtensions}，
 * 待办/已办/抄送列表只渲染扩展结果，不直接摊开全部流程变量。</p>
 *
 * <p>{@code displayExtensions} 推荐协议：</p>
 * <pre>
 * {
 *   "fields": [
 *     { "label": "车间", "value": "一车间" },
 *     { "label": "金额", "value": "1200" }
 *   ]
 * }
 * </pre>
 * 也可以直接放键值对象，或在启动变量 {@code businessParams.displayFields} 里传同样的字段数组。
 */
public interface FlowBusinessListDisplayAdapter {

    /**
     * 批量补齐流程列表项的业务对象名、业务摘要和展示扩展字段。
     */
    void enrich(List<FlowBusinessListDisplayItem> items);
}
