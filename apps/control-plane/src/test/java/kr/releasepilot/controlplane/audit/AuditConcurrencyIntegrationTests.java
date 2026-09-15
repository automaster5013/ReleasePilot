package kr.releasepilot.controlplane.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={
        "spring.datasource.url=${ANALYSIS_TEST_JDBC_URL:jdbc:h2:mem:audit-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${ANALYSIS_TEST_USERNAME:sa}",
        "spring.datasource.password=${ANALYSIS_TEST_PASSWORD:}",
        "spring.flyway.enabled=${ANALYSIS_TEST_FLYWAY:false}",
        "spring.jpa.hibernate.ddl-auto=${ANALYSIS_TEST_DDL:create-drop}"})
class AuditConcurrencyIntegrationTests {
    @Autowired AuditTrail trail;
    @Autowired AuditChainVerifier verifier;
    @Autowired AuditChainHeadRepository heads;
    @Autowired AuditArchiveDeliveryRepository deliveries;
    @Autowired AuditEventRepository events;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void initializeHead() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (!heads.existsById(1)) heads.saveAndFlush(AuditChainHead.genesis());
        });
    }

    @Test
    void concurrentWritersCommitUniqueContiguousSequencesAndArchiveDeliveries() throws Exception {
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        long archiveCount=deliveries.count();
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(8)) {
            var futures=new ArrayList<Future<AuditEvent>>();
            for(int i=0;i<8;i++) futures.add(executor.submit(() -> {
                await(start);
                return trail.record(event());
            }));
            start.countDown();
            var sequences=new ArrayList<Long>();
            for(var future:futures) sequences.add(future.get(15,TimeUnit.SECONDS).getChainSequence());
            assertThat(sequences).doesNotHaveDuplicates();
            assertThat(sequences.stream().sorted().toList()).containsExactly(
                    before.verifiedEvents()+1,before.verifiedEvents()+2,before.verifiedEvents()+3,
                    before.verifiedEvents()+4,before.verifiedEvents()+5,before.verifiedEvents()+6,
                    before.verifiedEvents()+7,before.verifiedEvents()+8);
        }
        var after=verifier.verify();
        assertThat(after.valid()).isTrue();
        assertThat(after.verifiedEvents()).isEqualTo(before.verifiedEvents()+8);
        assertThat(deliveries.count()).isEqualTo(archiveCount+8);
    }

    @Test
    void archiveBatchFailureCommitsAndNewWorkerRecoversInSeparateTransaction() {
        var transaction=new TransactionTemplate(transactionManager);
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        var now=Instant.parse("2030-01-01T00:00:00Z");
        var recorded=transaction.execute(status -> {
            var first=trail.record(event());
            var second=trail.record(event());
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(first),"availableAt",now.minusSeconds(2));
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(second),"availableAt",now.minusSeconds(1));
            return java.util.List.of(first,second);
        });
        var first=recorded.get(0);
        var second=recorded.get(1);
        var sink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("Bearer synthetic-commit-secret"))
                .when(sink).archive(org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick());

        transaction.executeWithoutResult(status -> {
            assertDelivery(first,AuditArchiveDelivery.Status.PENDING,1,"AUDIT_ARCHIVE_UNAVAILABLE",null);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(deliveryFor(first),"availableAt"))
                    .isEqualTo(now.plusSeconds(2));
            assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            var result=verifier.verify();
            assertThat(result.valid()).isTrue();
            assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+2);
            assertThat(result.headHash()).isEqualTo(second.getEventHash());
        });

        var recoveredSink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        var recoveredWorker=new AuditArchiveWorker(deliveries,events,recoveredSink,
                java.time.Clock.fixed(now.plusSeconds(2),java.time.ZoneOffset.UTC));
        transaction.executeWithoutResult(status -> recoveredWorker.tick());
        transaction.executeWithoutResult(status -> {
            assertDelivery(first,AuditArchiveDelivery.Status.DELIVERED,1,null,now.plusSeconds(2));
            assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            var result=verifier.verify();
            assertThat(result.valid()).isTrue();
            assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+2);
            assertThat(result.headHash()).isEqualTo(second.getEventHash());
            assertThat(events.findById(first.getId()).orElseThrow().getEventHash()).isEqualTo(first.getEventHash());
        });
        org.mockito.Mockito.verify(recoveredSink).archive(org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        org.mockito.Mockito.verifyNoMoreInteractions(recoveredSink);
    }

    @Test
    void archiveBatchRollbackLeavesBothEventsDueForRedelivery() {
        var transaction=new TransactionTemplate(transactionManager);
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        var now=Instant.parse("2031-01-01T00:00:00Z");
        var recorded=transaction.execute(status -> {
            var first=trail.record(event());
            var second=trail.record(event());
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(first),"availableAt",now.minusSeconds(2));
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(second),"availableAt",now.minusSeconds(1));
            return java.util.List.of(first,second);
        });
        var first=recorded.get(0);
        var second=recorded.get(1);
        var sink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic rollback failure"))
                .when(sink).archive(org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        transaction.executeWithoutResult(status -> {
            new AuditArchiveWorker(deliveries,events,sink,
                    java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick();
            deliveries.flush();
            assertDelivery(first,AuditArchiveDelivery.Status.PENDING,1,"AUDIT_ARCHIVE_UNAVAILABLE",null);
            assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> {
            assertDelivery(first,AuditArchiveDelivery.Status.PENDING,0,null,null);
            assertDelivery(second,AuditArchiveDelivery.Status.PENDING,0,null,null);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(deliveryFor(first),"availableAt"))
                    .isEqualTo(now.minusSeconds(2));
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(deliveryFor(second),"availableAt"))
                    .isEqualTo(now.minusSeconds(1));
            assertThat(verifier.verify().valid()).isTrue();
        });
        org.mockito.Mockito.doNothing().when(sink).archive(
                org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick());
        transaction.executeWithoutResult(status -> {
            assertDelivery(first,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            var result=verifier.verify();
            assertThat(result.valid()).isTrue();
            assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+2);
            assertThat(result.headHash()).isEqualTo(second.getEventHash());
            assertThat(events.findById(first.getId()).orElseThrow().getEventHash()).isEqualTo(first.getEventHash());
        });
        org.mockito.Mockito.verify(sink,org.mockito.Mockito.times(2)).archive(
                org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        // A successful sink call is not undone by the database rollback.
        org.mockito.Mockito.verify(sink,org.mockito.Mockito.times(2)).archive(
                org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(second.getId())));
    }

    @Test
    void committedHttpBatchFailureRecoversOnlyFailedEventWithSameEnvelope() throws Exception {
        var transaction=new TransactionTemplate(transactionManager);
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        var now=Instant.parse("2032-01-01T00:00:00Z");
        var recorded=transaction.execute(status -> {
            var first=trail.record(event());
            var second=trail.record(event());
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(first),"availableAt",now.minusSeconds(2));
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(second),"availableAt",now.minusSeconds(1));
            return java.util.List.of(first,second);
        });
        var first=recorded.get(0);
        var second=recorded.get(1);
        var requests=java.util.Collections.synchronizedList(new ArrayList<ArchiveRequest>());
        var failedOnce=new java.util.concurrent.atomic.AtomicBoolean();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/archive/",exchange -> {
            try {
                var key=exchange.getRequestHeaders().getFirst("Idempotency-Key");
                requests.add(new ArchiveRequest(exchange.getRequestMethod(),exchange.getRequestURI().getPath(),key,
                        new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));
                var response="Bearer synthetic-http-db-secret private-url".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                int status=first.getEventHash().equals(key)&&failedOnce.compareAndSet(false,true)?503:201;
                exchange.sendResponseHeaders(status,response.length);
                exchange.getResponseBody().write(response);
            } finally {exchange.close();}
        });
        server.start();
        try {
            var endpoint=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/archive/");
            var json=new tools.jackson.databind.ObjectMapper();
            var sink=new HttpAuditArchiveSink(json,endpoint,"synthetic-loopback-token");
            transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                    java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick());
            transaction.executeWithoutResult(status -> {
                assertDelivery(first,AuditArchiveDelivery.Status.PENDING,1,"AUDIT_ARCHIVE_UNAVAILABLE",null);
                assertThat(org.springframework.test.util.ReflectionTestUtils.getField(deliveryFor(first),"availableAt"))
                        .isEqualTo(now.plusSeconds(2));
                assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
                assertThat(verifier.verify().valid()).isTrue();
            });
            var recoveredSink=new HttpAuditArchiveSink(json,endpoint,"synthetic-loopback-token");
            transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,recoveredSink,
                    java.time.Clock.fixed(now.plusSeconds(2),java.time.ZoneOffset.UTC)).tick());
            transaction.executeWithoutResult(status -> {
                assertDelivery(first,AuditArchiveDelivery.Status.DELIVERED,1,null,now.plusSeconds(2));
                assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
                var result=verifier.verify();
                assertThat(result.valid()).isTrue();
                assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+2);
                assertThat(result.headHash()).isEqualTo(second.getEventHash());
            });
            var batchRequests=requests.stream().filter(value -> value.key().equals(first.getEventHash())
                    ||value.key().equals(second.getEventHash())).toList();
            assertThat(batchRequests.stream().map(ArchiveRequest::key).toList())
                    .containsExactly(first.getEventHash(),second.getEventHash(),first.getEventHash());
            assertThat(batchRequests).allSatisfy(value -> {
                assertThat(value.method()).isEqualTo("PUT");
                assertThat(value.body()).doesNotContain("synthetic-loopback-token","synthetic-http-db-secret");
            });
            assertThat(batchRequests.get(0)).isEqualTo(batchRequests.get(2));
            assertThat(batchRequests.get(0).path()).isEqualTo("/archive/"+first.getChainSequence()+"-"+first.getEventHash()+".json");
            assertThat(json.readTree(batchRequests.get(0).body()).get("eventHash").asString()).isEqualTo(first.getEventHash());
        } finally {server.stop(0);}
    }

    private record ArchiveRequest(String method,String path,String key,String body) {}

    @Test
    void httpConflictRemainsPendingWithoutOverwritingReceiverOrBlockingNextEvent() throws Exception {
        var transaction=new TransactionTemplate(transactionManager);
        var now=Instant.parse("2034-01-01T00:00:00Z");
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        var recorded=transaction.execute(status -> {
            var first=trail.record(event());
            var second=trail.record(event());
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(first),"availableAt",now.minusSeconds(2));
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(second),"availableAt",now.minusSeconds(1));
            return java.util.List.of(first,second);
        });
        var first=recorded.get(0);
        var second=recorded.get(1);
        var conflicting=new ArchiveRequest("PUT","/archive/"+first.getChainSequence()+"-"+first.getEventHash()+".json",
                first.getEventHash(),"{\"synthetic\":\"existing conflicting object\"}");
        var stored=new java.util.concurrent.ConcurrentHashMap<String,ArchiveRequest>();
        stored.put(first.getEventHash(),conflicting);
        var requests=java.util.Collections.synchronizedList(new ArrayList<ArchiveRequest>());
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/archive/",exchange -> {
            try {
                var request=new ArchiveRequest(exchange.getRequestMethod(),exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                        new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                requests.add(request);
                var previous=stored.putIfAbsent(request.key(),request);
                var response="Bearer synthetic-conflict-secret private-url".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(previous==null?201:previous.equals(request)?200:409,response.length);
                exchange.getResponseBody().write(response);
            } finally {exchange.close();}
        });
        server.start();
        try {
            var endpoint=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/archive/");
            var sink=new HttpAuditArchiveSink(new tools.jackson.databind.ObjectMapper(),endpoint,"");
            transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                    java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick());
            transaction.executeWithoutResult(status -> {
                assertDelivery(first,AuditArchiveDelivery.Status.PENDING,1,"AUDIT_ARCHIVE_UNAVAILABLE",null);
                assertThat(org.springframework.test.util.ReflectionTestUtils.getField(deliveryFor(first),"availableAt"))
                        .isEqualTo(now.plusSeconds(2));
                assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
                assertThat(verifier.verify().valid()).isTrue();
            });
            transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                    java.time.Clock.fixed(now.plusSeconds(2),java.time.ZoneOffset.UTC)).tick());
            transaction.executeWithoutResult(status -> {
                assertDelivery(first,AuditArchiveDelivery.Status.PENDING,2,"AUDIT_ARCHIVE_UNAVAILABLE",null);
                assertThat(org.springframework.test.util.ReflectionTestUtils.getField(deliveryFor(first),"availableAt"))
                        .isEqualTo(now.plusSeconds(6));
                assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
                var result=verifier.verify();
                assertThat(result.valid()).isTrue();
                assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+2);
                assertThat(result.headHash()).isEqualTo(second.getEventHash());
            });
            var targetRequests=requests.stream().filter(value -> value.key().equals(first.getEventHash())
                    ||value.key().equals(second.getEventHash())).toList();
            assertThat(targetRequests.stream().map(ArchiveRequest::key).toList())
                    .containsExactly(first.getEventHash(),second.getEventHash(),first.getEventHash());
            assertThat(targetRequests.get(0)).isEqualTo(targetRequests.get(2));
            assertThat(targetRequests).allSatisfy(value -> assertThat(value.method()).isEqualTo("PUT"));
            assertThat(stored.get(first.getEventHash())).isEqualTo(conflicting);
            assertThat(stored.get(second.getEventHash())).isEqualTo(targetRequests.get(1));
        } finally {server.stop(0);}
    }

    @Test
    void httpReceiverDeduplicatesRedeliveryAfterDatabaseRollback() throws Exception {
        var transaction=new TransactionTemplate(transactionManager);
        var now=Instant.parse("2033-01-01T00:00:00Z");
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        var event=transaction.execute(status -> trail.record(event()));
        var requests=java.util.Collections.synchronizedList(new ArrayList<ArchiveRequest>());
        var stored=new java.util.concurrent.ConcurrentHashMap<String,ArchiveRequest>();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/archive/",exchange -> {
            try {
                var request=new ArchiveRequest(exchange.getRequestMethod(),exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                        new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                requests.add(request);
                var previous=stored.putIfAbsent(request.key(),request);
                // This test receiver accepts an identical retry, but rejects a conflicting envelope.
                exchange.sendResponseHeaders(previous==null?201:previous.equals(request)?200:409,-1);
            } finally {exchange.close();}
        });
        server.start();
        try {
            var endpoint=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/archive/");
            var sink=new HttpAuditArchiveSink(new tools.jackson.databind.ObjectMapper(),endpoint,"");
            transaction.executeWithoutResult(status -> {
                new AuditArchiveWorker(deliveries,events,sink,
                        java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick();
                deliveries.flush();
                assertDelivery(event,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
                status.setRollbackOnly();
            });
            transaction.executeWithoutResult(status -> {
                assertDelivery(event,AuditArchiveDelivery.Status.PENDING,0,null,null);
                assertThat(verifier.verify().valid()).isTrue();
            });
            assertThat(stored).containsKey(event.getEventHash());
            var recoveredSink=new HttpAuditArchiveSink(new tools.jackson.databind.ObjectMapper(),endpoint,"");
            transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,recoveredSink,
                    java.time.Clock.fixed(now.plusSeconds(1),java.time.ZoneOffset.UTC)).tick());
            transaction.executeWithoutResult(status -> {
                assertDelivery(event,AuditArchiveDelivery.Status.DELIVERED,0,null,now.plusSeconds(1));
                var result=verifier.verify();
                assertThat(result.valid()).isTrue();
                assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+1);
                assertThat(result.headHash()).isEqualTo(event.getEventHash());
            });
            var targetRequests=requests.stream().filter(value -> value.key().equals(event.getEventHash())).toList();
            assertThat(targetRequests).hasSize(2);
            assertThat(targetRequests.get(0)).isEqualTo(targetRequests.get(1));
            assertThat(targetRequests.get(0).method()).isEqualTo("PUT");
            assertThat(targetRequests.get(0).path()).isEqualTo("/archive/"+event.getChainSequence()+"-"+event.getEventHash()+".json");
            assertThat(stored.values().stream().filter(value -> value.key().equals(event.getEventHash())).toList())
                    .containsExactly(targetRequests.get(0));
            int requestCount=requests.size();
            transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,recoveredSink,
                    java.time.Clock.fixed(now.plusSeconds(2),java.time.ZoneOffset.UTC)).tick());
            assertThat(requests).hasSize(requestCount);
        } finally {server.stop(0);}
    }

    private AuditArchiveDelivery deliveryFor(AuditEvent event) {
        return deliveries.findAll().stream().filter(value -> value.auditEventId().equals(event.getId()))
                .findFirst().orElseThrow();
    }

    private void assertDelivery(AuditEvent event,AuditArchiveDelivery.Status expectedStatus,int attempts,
            String error,Instant deliveredAt) {
        var stored=deliveryFor(event);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(stored,"status")).isEqualTo(expectedStatus);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(stored,"attempts")).isEqualTo(attempts);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(stored,"lastError")).isEqualTo(error);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(stored,"deliveredAt")).isEqualTo(deliveredAt);
    }

    @Test
    void verifierWaitsForWriterCommitAndThenSeesConsistentHead() throws Exception {
        var before=verifier.verify();
        var writerHoldingLock=new CountDownLatch(1);
        var releaseWriter=new CountDownLatch(1);
        var verifierStarted=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var writer=executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                heads.lockById(1);
                var saved=trail.record(event());
                writerHoldingLock.countDown();
                await(releaseWriter);
                return saved;
            }));
            try {
                assertThat(writerHoldingLock.await(10,TimeUnit.SECONDS)).isTrue();
                var verification=executor.submit(() -> {
                    verifierStarted.countDown();
                    return verifier.verify();
                });
                assertThat(verifierStarted.await(10,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> verification.get(200,TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseWriter.countDown();
                var saved=writer.get(10,TimeUnit.SECONDS);
                var result=verification.get(10,TimeUnit.SECONDS);
                assertThat(result.valid()).isTrue();
                assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+1);
                assertThat(result.headHash()).isEqualTo(saved.getEventHash());
            } finally {
                releaseWriter.countDown();
            }
        }
    }

    private static AuditEvent event() {
        return AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),Instant.now());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("Test synchronization timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test interrupted",exception);
        }
    }
}
