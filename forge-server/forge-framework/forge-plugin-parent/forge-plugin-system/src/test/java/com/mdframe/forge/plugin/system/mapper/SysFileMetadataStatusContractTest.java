package com.mdframe.forge.plugin.system.mapper;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.mdframe.forge.plugin.system.entity.SysFileMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SysFileMetadataStatusContractTest {

    @Test
    void statusIsTheDeclaredLogicalDeleteColumn() throws Exception {
        TableLogic tableLogic = SysFileMetadata.class.getDeclaredField("status").getAnnotation(TableLogic.class);

        assertThat(tableLogic).isNotNull();
        assertThat(tableLogic.value()).isEqualTo("1");
        assertThat(tableLogic.delval()).isEqualTo("0");
    }

    @Test
    void customQueriesAndWritesOnlyOperateOnActiveFiles() throws Exception {
        String mapper = Files.readString(Path.of("src/main/resources/mapper/SysFileMetadataMapper.xml"));

        assertThat(mapper).contains("WHERE status = 1");
        assertThat(mapper).contains("AND status = 1");
        assertThat(mapper).contains("SET status = 0");
        assertThat(mapper).contains("AND (is_private = 0 OR uploader_id = #{currentUserId})");
        assertThat(mapper).doesNotContain("DELETE FROM sys_file_metadata");
    }

    @Test
    void migrationMakesStatusNonNullAndNormalizesInvalidRows() throws Exception {
        String migration = Files.readString(Path.of(
                "../../../db/migration/V1.0.189__normalize_file_metadata_status.sql"));

        assertThat(migration).contains("status IS NULL OR status NOT IN (0, 1)");
        assertThat(migration).contains("status TINYINT(1) NOT NULL DEFAULT 1");
    }
}
