package com.mdframe.forge.starter.config.config;

import lombok.Data;

/**
 * 水印配置
 */
@Data
public class WatermarkConfig {

    /**
     * 是否启用水印
     */
    private Boolean enable = false;

    /**
     * 水印内容
     */
    private String content = "MDFrame";

    /**
     * 水印透明度 (0.0-1.0)
     */
    private Double opacity = 0.1;

    /**
     * 水印字体大小
     */
    private Integer fontSize = 16;

    /**
     * 水印字体颜色
     */
    private String fontColor = "#cccccc";

    /**
     * 水印旋转角度
     */
    private Integer rotate = -20;

    /**
     * 水印间距X轴
     */
    private Integer gapX = 200;

    /**
     * 水印间距Y轴
     */
    private Integer gapY = 200;

    /**
     * 水印偏移X轴
     */
    private Integer offsetX = 0;

    /**
     * 水印偏移Y轴
     */
    private Integer offsetY = 0;

    /**
     * 水印层级
     */
    private Integer zIndex = 1000;

    /**
     * 是否显示时间戳
     */
    private Boolean showTimestamp = false;

    /**
     * 时间戳格式
     */
    private String timestampFormat = "yyyy-MM-dd HH:mm:ss";

    /**
     * 是否给 Excel 导出加水印
     */
    private Boolean excelWatermark = false;

    /**
     * Excel 水印是否包含租户配置的系统名称（sys_tenant.system_name）
     */
    private Boolean excelShowSystemName = true;

    /**
     * Excel 水印是否包含操作人姓名
     */
    private Boolean excelShowUsername = true;

    /**
     * Excel 水印是否包含登录账号（username）
     */
    private Boolean excelShowAccount = false;

    /**
     * Excel 水印是否包含手机号（完整显示，用于泄露追溯）
     */
    private Boolean excelShowPhone = false;

    /**
     * Excel 水印是否包含导出时间（复用 timestampFormat）
     */
    private Boolean excelShowTime = false;
}