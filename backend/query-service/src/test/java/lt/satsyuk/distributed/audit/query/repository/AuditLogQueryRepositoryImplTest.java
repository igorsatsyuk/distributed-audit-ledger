package lt.satsyuk.distributed.audit.query.repository;

import lt.satsyuk.distributed.audit.query.service.AuditLogFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryRepositoryImplTest {

    @Mock
    private DatabaseClient databaseClient;

    private AuditLogQueryRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new AuditLogQueryRepositoryImpl(databaseClient);
    }

    // ---- escapeLikePattern ------------------------------------------------

    @Test
    void escapeLikePattern_leavesPlainTextUnchanged() {
        assertEquals("hello", repository.escapeLikePattern("hello"));
    }

    @Test
    void escapeLikePattern_escapesPercent() {
        assertEquals("100\\%", repository.escapeLikePattern("100%"));
    }

    @Test
    void escapeLikePattern_escapesUnderscore() {
        assertEquals("some\\_value", repository.escapeLikePattern("some_value"));
    }

    @Test
    void escapeLikePattern_escapesBackslash() {
        assertEquals("C:\\\\path", repository.escapeLikePattern("C:\\path"));
    }

    @Test
    void escapeLikePattern_escapesAllSpecialCharsInCombination() {
        assertEquals("\\%\\_\\\\", repository.escapeLikePattern("%_\\"));
    }

    // ---- findByFilter with search -----------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void findByFilter_withSearch_bindsSearchPattern() {
        // Arrange fluent mock chain
        DatabaseClient.GenericExecuteSpec execSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(execSpec);
        when(execSpec.bind(anyString(), any())).thenReturn(execSpec);

        org.springframework.r2dbc.core.RowsFetchSpec<lt.satsyuk.distributed.audit.query.model.AuditEventRecord> rowsSpec =
                mock(org.springframework.r2dbc.core.RowsFetchSpec.class);
        when(execSpec.map(any(java.util.function.BiFunction.class))).thenReturn(rowsSpec);
        when(rowsSpec.all()).thenReturn(Flux.empty());

        AuditLogFilter filter = new AuditLogFilter(
                null, null, null, null,
                "10.0.0.5",
                10, 0L
        );

        // Act
        List<lt.satsyuk.distributed.audit.query.model.AuditEventRecord> result =
                repository.findByFilter(filter).collectList().block();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
        // Verify that bind was called with the expected search pattern
        verify(execSpec).bind("searchPattern", "%10.0.0.5%");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByFilter_withSearchContainingSpecialChars_escapesPattern() {
        DatabaseClient.GenericExecuteSpec execSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(execSpec);
        when(execSpec.bind(anyString(), any())).thenReturn(execSpec);

        org.springframework.r2dbc.core.RowsFetchSpec<lt.satsyuk.distributed.audit.query.model.AuditEventRecord> rowsSpec =
                mock(org.springframework.r2dbc.core.RowsFetchSpec.class);
        when(execSpec.map(any(java.util.function.BiFunction.class))).thenReturn(rowsSpec);
        when(rowsSpec.all()).thenReturn(Flux.empty());

        AuditLogFilter filter = new AuditLogFilter(
                null, null, null, null,
                "100%_done",
                10, 0L
        );

        repository.findByFilter(filter).collectList().block();

        verify(execSpec).bind("searchPattern", "%100\\%\\_done%");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByFilter_withoutSearch_doesNotBindSearchPattern() {
        DatabaseClient.GenericExecuteSpec execSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(execSpec);
        when(execSpec.bind(anyString(), any())).thenReturn(execSpec);

        org.springframework.r2dbc.core.RowsFetchSpec<lt.satsyuk.distributed.audit.query.model.AuditEventRecord> rowsSpec =
                mock(org.springframework.r2dbc.core.RowsFetchSpec.class);
        when(execSpec.map(any(java.util.function.BiFunction.class))).thenReturn(rowsSpec);
        when(rowsSpec.all()).thenReturn(Flux.empty());

        AuditLogFilter filter = new AuditLogFilter(
                null, null, null, null,
                "   ",
                10, 0L
        );

        repository.findByFilter(filter).collectList().block();

        verify(execSpec, never()).bind(eq("searchPattern"), any());
    }
}

