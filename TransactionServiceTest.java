package com.example.fde;

import com.example.fde.api.TransactionRequest;
import com.example.fde.model.TransactionType;
import com.example.fde.repository.TransactionRepository;
import com.example.fde.service.TransactionProcessor;
import com.example.fde.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TransactionServiceTest {
    @Autowired TransactionService service;
    @Autowired TransactionRepository repository;
    @Autowired TransactionProcessor processor;

    @Test void duplicateBusinessTransactionIsNotInsertedTwice() {
        var request = new TransactionRequest("TX-1", "KEY-1", "ACC-1", TransactionType.CREDIT,
                new BigDecimal("100"), "USD", 1, false);
        var first = service.submit(request);
        var duplicate = service.submit(new TransactionRequest("TX-1", "KEY-2", "ACC-1",
                TransactionType.CREDIT, new BigDecimal("100"), "USD", 1, false));
        assertFalse(first.duplicateRequest());
        assertTrue(duplicate.duplicateRequest());
        assertEquals(1, repository.count());
    }

    @Test void futureSequenceWaitsUntilPreviousSequenceArrives() {
        service.submit(new TransactionRequest("TX-2", "KEY-2", "ACC-2", TransactionType.CREDIT,
                new BigDecimal("40"), "USD", 2, false));
        processor.processPending();
        assertEquals("RECEIVED", service.all().get(0).status().name());
        service.submit(new TransactionRequest("TX-1", "KEY-1", "ACC-2", TransactionType.CREDIT,
                new BigDecimal("100"), "USD", 1, false));
        processor.processPending();
        assertEquals(2, service.all().stream().filter(t -> t.status().name().equals("PROCESSED")).count());
    }

    @Test void transientFailureCanBeRetriedWithoutDuplicateBalanceEffect() {
        service.submit(new TransactionRequest("TX-3", "KEY-3", "ACC-3", TransactionType.CREDIT,
                new BigDecimal("25"), "USD", 1, true));
        processor.processPending();
        assertEquals("RETRY_PENDING", service.all().get(0).status().name());
        processor.processPending();
        assertEquals("PROCESSED", service.all().get(0).status().name());
    }
}
