package com.flink.platform.web.flowrun;

import com.flink.platform.dao.entity.JobFlowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FlowRunCreationServiceTest {

    @Mock
    private FlowRunCreationService.TransactionService transactionService;

    @Mock
    private LegacyJobListService legacyJobListService;

    private boolean previousTransactionState;

    @BeforeEach
    void rememberTransactionState() {
        previousTransactionState = TransactionSynchronizationManager.isActualTransactionActive();
    }

    @AfterEach
    void restoreTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(previousTransactionState);
    }

    @Test
    void rejectsCreationInsideCallerTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var service = new FlowRunCreationService(transactionService, legacyJobListService);
        var candidate = new JobFlowRun();
        candidate.setFlowId(1L);

        assertThrows(IllegalStateException.class, () -> service.tryCreateFlowRun(candidate));
        verifyNoInteractions(transactionService, legacyJobListService);
    }
}
