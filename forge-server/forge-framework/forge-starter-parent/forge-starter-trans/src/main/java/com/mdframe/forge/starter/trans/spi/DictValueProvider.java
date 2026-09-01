package com.mdframe.forge.starter.trans.spi;

import java.util.List;
import java.util.Map;

public interface DictValueProvider {

    String getLabel(String dictType, String key);

    default List<String> listLabels(String dictType) {
        return List.of();
    }

    default String getValue(String dictType, String labelOrValue) {
        return null;
    }

    default String getOrgName(String orgId) {
        return null;
    }

    default Map<String, String> batchGetOrgNames(List<String> orgIds) {
        return Map.of();
    }

    default String getUserName(String userId) {
        return null;
    }

    default Map<String, String> batchGetUserNames(List<String> userIds) {
        return Map.of();
    }

    default String getRegionName(String regionCode) {
        return null;
    }

    default Map<String, String> batchGetRegionNames(List<String> regionCodes) {
        return Map.of();
    }

    default String getFileUrl(String fileId) {
        return null;
    }

    default Map<String, String> batchGetFileUrls(List<String> fileIds) {
        return Map.of();
    }

    default String getFileName(String fileId) {
        return null;
    }

    default Map<String, String> batchGetFileNames(List<String> fileIds) {
        return Map.of();
    }
}
