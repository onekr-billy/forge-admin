package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessAppOpenInfoVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Business app mobile low-code entry")
class BusinessAppOpenServiceTest {

    @Test
    @DisplayName("mobile runtime entries open the H5 low-code page with config key")
    void opensMobileRuntimeInH5() {
        BusinessAppMapper businessAppMapper = mock(BusinessAppMapper.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessAppOpenService service = new BusinessAppOpenService(businessAppMapper, crudConfigMapper);

        AiBusinessApp app = new AiBusinessApp();
        app.setId(1950000000000001201L);
        app.setAppName("门店预售登记");
        app.setAppType("MOBILE");
        app.setEntryMode("RUNTIME");
        app.setConfigKey("ps_presale_order");
        app.setStatus(1);
        app.setOptions("{}");

        AiCrudConfig config = new AiCrudConfig();
        config.setStatus("0");
        config.setPublishStatus("PUBLISHED");
        when(crudConfigMapper.selectByConfigKey(anyLong(), org.mockito.ArgumentMatchers.eq("ps_presale_order")))
                .thenReturn(config);

        BusinessAppOpenInfoVO result = service.buildRuntimeOpenInfo(app);

        assertEquals("H5", result.getOpenType());
        assertEquals("AVAILABLE", result.getRuntimeStatus());
        assertTrue(result.getTargetUrl().startsWith("/forge-h5/#/pages/lowcode-runtime?"));
        assertTrue(result.getTargetUrl().contains("configKey=ps_presale_order"));
        assertTrue(result.getTargetUrl().contains("title=%E9%97%A8%E5%BA%97%E9%A2%84%E5%94%AE%E7%99%BB%E8%AE%B0"));
    }
}
