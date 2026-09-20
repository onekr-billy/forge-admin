package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditFilterOptionsVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataAuditFilterOptionServiceTest {

    @Test
    void buildsAuthorizedApplicationPageAndBusinessFieldHierarchy() {
        ObjectMapper objectMapper = new ObjectMapper();
        DataAuditFilterOptionService service = new DataAuditFilterOptionService(
                null,
                null,
                null,
                null,
                null,
                new DataAuditValueNormalizer(objectMapper),
                objectMapper);
        AiBusinessApplication application = new AiBusinessApplication();
        application.setId(1L);
        application.setApplicationName("采购管理");
        application.setApplicationCode("purchase");
        application.setOptions("""
                {
                  "inAppBuilder": {
                    "nodes": [
                      {
                        "id": "page_purchase",
                        "type": "page",
                        "title": "采购申请",
                        "objectRef": {"objectId": 10, "objectCode": "purchase_request"}
                      },
                      {
                        "id": "page_unauthorized",
                        "type": "page",
                        "title": "未授权页面",
                        "objectRef": {"objectId": 99, "objectCode": "secret"}
                      }
                    ]
                  }
                }
                """);
        BusinessApplicationObjectVO object = new BusinessApplicationObjectVO();
        object.setObjectId(10L);
        object.setObjectCode("purchase_request");
        object.setObjectName("采购申请");
        object.setModelSchema("""
                {
                  "fields": [
                    {"field": "id", "columnName": "id", "label": "主键"},
                    {"field": "amount", "columnName": "amount", "label": "采购金额"}
                  ]
                }
                """);

        DataAuditFilterOptionsVO.ApplicationOption result = service.toApplicationOption(
                application, List.of(object));

        assertEquals("采购管理", result.getApplicationName());
        assertEquals(1, result.getPages().size());
        assertEquals("采购申请", result.getPages().get(0).getPageName());
        assertEquals("10", result.getPages().get(0).getObjectId());
        assertEquals(1, result.getPages().get(0).getFields().size());
        assertEquals("amount", result.getPages().get(0).getFields().get(0).getFieldCode());
        assertEquals("采购金额", result.getPages().get(0).getFields().get(0).getFieldLabel());
    }
}
