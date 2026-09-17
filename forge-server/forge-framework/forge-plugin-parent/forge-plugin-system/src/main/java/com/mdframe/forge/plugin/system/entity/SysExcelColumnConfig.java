package com.mdframe.forge.plugin.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Excel列配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_excel_column_config")
public class SysExcelColumnConfig extends BaseEntity {
    
    
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 关联配置键
     */
    private String configKey;

    /**
     * 字段名
     */
    private String fieldName;

    /**
     * 列名（表头）
     */
    private String columnName;

    /**
     * 列宽
     */
    private Integer width;

    /**
     * 排序
     */
    private Integer orderNum;

    /**
     * 是否导出
     */
    private Boolean export;

    /**
     * 日期格式
     */
    private String dateFormat;

    /**
     * 数字格式
     */
    private String numberFormat;

    /**
     * 字典类型
     */
    private String dictType;

    /**
     * 是否可导入
     */
    private Boolean importable;

    /**
     * 是否必填
     */
    private Boolean required;

    /**
     * 示例值
     */
    private String exampleValue;

    /**
     * 校验规则
     */
    private String validationRule;

    /**
     * 校验失败提示
     */
    private String validationMessage;

    /**
     * 列类型：TEXT/IMAGE
     */
    private String columnType;

    /**
     * 图片列导出宽度(像素)
     */
    private Integer imageWidth;

    /**
     * 图片列导出高度(像素)
     */
    private Integer imageHeight;

    /**
     * 图片列最大图片数量
     */
    private Integer imageMaxCount;
}
