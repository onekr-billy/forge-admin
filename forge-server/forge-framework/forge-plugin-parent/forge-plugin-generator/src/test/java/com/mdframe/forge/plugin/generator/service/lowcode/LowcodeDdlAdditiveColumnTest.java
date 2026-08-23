package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.service.DynamicCrudRepository;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.MySqlRuntimeDatabaseDialect;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeDatabaseDialectFactory;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Lowcode additive managed column")
class LowcodeDdlAdditiveColumnTest {

    @Test
    @DisplayName("existing incompatible column fails without altering data structure")
    void rejectsIncompatibleExistingColumn() {
        Fixture fixture = fixture();
        when(fixture.repository.listColumnMetadata(fixture.context, "presale_registration"))
                .thenReturn(Map.of("flow_status", new LowcodeDdlRepository.ColumnMetadata(
                        "flow_status", "bigint", "YES", null, "", "旧流程状态", "")));

        assertThrows(BusinessException.class,
                () -> fixture.service.executeAdditiveColumn(schema(), "flow_status"));

        verify(fixture.repository, never()).executeDdl(
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("existing compatible managed column is reused without DDL")
    void reusesCompatibleExistingColumn() {
        Fixture fixture = fixture();
        LowcodeDdlRepository.ColumnMetadata metadata = new LowcodeDdlRepository.ColumnMetadata(
                "flow_status", "varchar(32)", "NO", "DRAFT", "", "流程状态", "");
        when(fixture.repository.listColumnMetadata(fixture.context, "presale_registration"))
                .thenReturn(Map.of("flow_status", metadata));
        when(fixture.repository.characterCapacity(metadata)).thenReturn(32);

        fixture.service.executeAdditiveColumn(schema(), "flow_status");

        verify(fixture.repository, never()).executeDdl(
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    private Fixture fixture() {
        LowcodeSchemaValidator validator = mock(LowcodeSchemaValidator.class);
        LowcodeDdlRepository repository = mock(LowcodeDdlRepository.class);
        DynamicCrudRepository crudRepository = mock(DynamicCrudRepository.class);
        LowcodeRuntimeDataSourceResolver resolver = mock(LowcodeRuntimeDataSourceResolver.class);
        RuntimeDatabaseDialectFactory dialectFactory = mock(RuntimeDatabaseDialectFactory.class);
        LowcodeRuntimeDataSourceContext context = LowcodeRuntimeDataSourceContext.master("presale_registration");
        when(resolver.resolve(org.mockito.ArgumentMatchers.any(LowcodeModelSchema.class)))
                .thenReturn(context);
        when(dialectFactory.resolve(context)).thenReturn(new MySqlRuntimeDatabaseDialect());
        when(repository.tableExists(context, "presale_registration")).thenReturn(true);
        return new Fixture(
                new LowcodeDdlService(validator, repository, crudRepository, resolver, dialectFactory),
                repository,
                context);
    }

    private LowcodeModelSchema schema() {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField("flowStatus");
        field.setColumnName("flow_status");
        field.setLabel("流程状态");
        field.setDataType("varchar");
        field.setLength(32);
        field.setRequired(true);
        field.setDefaultValue("DRAFT");
        field.setSystemField(false);
        LowcodeModelSchema schema = new LowcodeModelSchema();
        schema.setFields(List.of(field));
        return schema;
    }

    private record Fixture(LowcodeDdlService service,
                           LowcodeDdlRepository repository,
                           LowcodeRuntimeDataSourceContext context) {
    }
}
