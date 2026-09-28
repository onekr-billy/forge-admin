package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessQuantityQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityBalanceMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLedgerMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLockMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessQuantityBalanceVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessQuantityLedgerVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessQuantityLockVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("BusinessQuantityQueryService")
class BusinessQuantityQueryServiceTest {

    @Test
    @DisplayName("queries balance page")
    void queriesBalancePage() {
        Map<String, Object[]> calls = new LinkedHashMap<>();
        BusinessQuantityBalanceMapper balanceMapper = mapperProxy(BusinessQuantityBalanceMapper.class, Map.of(
                "selectBalancePage", args -> {
                    calls.put("balance", args);
                    Page<BusinessQuantityBalanceVO> page = castPage(args[0]);
                    BusinessQuantityBalanceVO row = new BusinessQuantityBalanceVO();
                    row.setAccountCode("warehouse-A");
                    row.setItemCode("item-1");
                    row.setAvailableQuantity(8L);
                    page.setRecords(List.of(row));
                    page.setTotal(1);
                    return page;
                }
        ));
        BusinessQuantityQueryService service = new BusinessQuantityQueryService(balanceMapper,
                mapperProxy(BusinessQuantityLedgerMapper.class, Map.of()),
                mapperProxy(BusinessQuantityLockMapper.class, Map.of()));
        BusinessQuantityQueryDTO query = new BusinessQuantityQueryDTO();
        query.setAccountCode("warehouse-A");
        query.setPageNum(2);
        query.setPageSize(20);

        Page<BusinessQuantityBalanceVO> page = service.selectBalancePage(query);

        assertEquals(2, page.getCurrent());
        assertEquals(20, page.getSize());
        assertEquals(1, page.getTotal());
        assertEquals("item-1", page.getRecords().get(0).getItemCode());
        assertEquals(1L, calls.get("balance")[1]);
        assertEquals("warehouse-A", ((BusinessQuantityQueryDTO) calls.get("balance")[2]).getAccountCode());
    }

    @Test
    @DisplayName("queries ledger page")
    void queriesLedgerPage() {
        BusinessQuantityLedgerMapper ledgerMapper = mapperProxy(BusinessQuantityLedgerMapper.class, Map.of(
                "selectLedgerPage", args -> {
                    Page<BusinessQuantityLedgerVO> page = castPage(args[0]);
                    BusinessQuantityLedgerVO row = new BusinessQuantityLedgerVO();
                    row.setOperationType("INBOUND");
                    row.setSourceRecordId("1001");
                    page.setRecords(List.of(row));
                    page.setTotal(1);
                    return page;
                }
        ));
        BusinessQuantityQueryService service = new BusinessQuantityQueryService(
                mapperProxy(BusinessQuantityBalanceMapper.class, Map.of()),
                ledgerMapper,
                mapperProxy(BusinessQuantityLockMapper.class, Map.of()));

        Page<BusinessQuantityLedgerVO> page = service.selectLedgerPage(new BusinessQuantityQueryDTO());

        assertEquals(1, page.getTotal());
        assertEquals("INBOUND", page.getRecords().get(0).getOperationType());
        assertEquals("1001", page.getRecords().get(0).getSourceRecordId());
    }

    @Test
    @DisplayName("queries lock page")
    void queriesLockPage() {
        BusinessQuantityLockMapper lockMapper = mapperProxy(BusinessQuantityLockMapper.class, Map.of(
                "selectLockPage", args -> {
                    Page<BusinessQuantityLockVO> page = castPage(args[0]);
                    BusinessQuantityLockVO row = new BusinessQuantityLockVO();
                    row.setLockStatus("LOCKED");
                    row.setRemainingQuantity(6L);
                    page.setRecords(List.of(row));
                    page.setTotal(1);
                    return page;
                }
        ));
        BusinessQuantityQueryService service = new BusinessQuantityQueryService(
                mapperProxy(BusinessQuantityBalanceMapper.class, Map.of()),
                mapperProxy(BusinessQuantityLedgerMapper.class, Map.of()),
                lockMapper);

        Page<BusinessQuantityLockVO> page = service.selectLockPage(null);

        assertEquals(1, page.getTotal());
        assertEquals("LOCKED", page.getRecords().get(0).getLockStatus());
        assertEquals(6L, page.getRecords().get(0).getRemainingQuantity());
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapperProxy(Class<T> mapperType, Map<String, Function<Object[], Object>> handlers) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxy, method, args) -> {
                    Function<Object[], Object> handler = handlers.get(method.getName());
                    if (handler != null) {
                        return handler.apply(args == null ? new Object[]{} : args);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> Page<T> castPage(Object value) {
        return (Page<T>) value;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE || returnType == Long.TYPE || returnType == Short.TYPE || returnType == Byte.TYPE) {
            return 0;
        }
        if (returnType == Float.TYPE || returnType == Double.TYPE) {
            return 0D;
        }
        return null;
    }
}
