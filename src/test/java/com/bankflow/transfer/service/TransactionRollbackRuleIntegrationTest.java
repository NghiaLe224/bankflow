package com.bankflow.transfer.service;

import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import({
        TransactionRollbackRuleIntegrationTest.RollbackRuleTestService.class,
        TransactionRollbackRuleIntegrationTest.RequiredInnerService.class,
        TransactionRollbackRuleIntegrationTest.RequiresNewInnerService.class,
        TransactionRollbackRuleIntegrationTest.PropagationOuterService.class,
        TransactionRollbackRuleIntegrationTest.SelfInvocationTestService.class
})
class TransactionRollbackRuleIntegrationTest {

    @Autowired
    private RollbackRuleTestService rollbackRuleTestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropagationOuterService propagationOuterService;

    @Autowired
    private SelfInvocationTestService selfInvocationTestService;

    @Test
    void runtimeException_shouldRollbackByDefault() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = new UserEntity(
                "Runtime Rollback Test",
                "runtime-" + suffix + "@test.com"
        );

        assertThrows(
                IllegalStateException.class,
                () -> rollbackRuleTestService
                        .saveUserThenThrowRuntime(user)
        );

        assertNotNull(user.getId());

        assertFalse(
                userRepository.existsById(user.getId())
        );
    }

    @Test
    void checkedException_shouldCommitByDefault() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = new UserEntity(
                "Checked Exception Test",
                "checked-" + suffix + "@test.com"
        );

        try {
            assertThrows(
                    IOException.class,
                    () -> rollbackRuleTestService
                            .saveUserThenThrowChecked(user)
            );

            assertNotNull(user.getId());

            assertTrue(
                    userRepository.existsById(user.getId())
            );
        } finally {
            if (user.getId() != null &&
                    userRepository.existsById(user.getId())) {
                userRepository.deleteById(user.getId());
            }
        }
    }

    @Test
    void checkedException_shouldRollback_whenRollbackForIsConfigured() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = new UserEntity(
                "Checked Rollback Test",
                "checked-rollback-" + suffix + "@test.com"
        );

        assertThrows(
                IOException.class,
                () -> rollbackRuleTestService
                        .saveUserThenThrowCheckedWithRollback(user)
        );

        assertNotNull(user.getId());

        assertFalse(
                userRepository.existsById(user.getId())
        );
    }

    @Test
    void required_shouldJoinOuterTransaction_andRollbackWithOuter() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = new UserEntity(
                "Required Propagation Test",
                "required-" + suffix + "@test.com"
        );

        assertThrows(
                IllegalStateException.class,
                () -> propagationOuterService
                        .saveWithRequiredThenFail(user)
        );

        assertNotNull(user.getId());

        assertFalse(
                userRepository.existsById(user.getId())
        );
    }

    @Test
    void requiresNew_shouldCommitInnerTransaction_evenWhenOuterRollsBack() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = new UserEntity(
                "Requires New Test",
                "requires-new-" + suffix + "@test.com"
        );

        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> propagationOuterService
                            .saveWithRequiresNewThenFail(user)
            );

            assertNotNull(user.getId());

            assertTrue(
                    userRepository.existsById(user.getId())
            );
        } finally {
            if (user.getId() != null &&
                    userRepository.existsById(user.getId())) {
                userRepository.deleteById(user.getId());
            }
        }
    }

    @Test
    void transactionalMethod_shouldRollback_whenCalledThroughProxy() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = new UserEntity(
                "Proxy Call Test",
                "proxy-" + suffix + "@test.com"
        );

        assertThrows(
                IllegalStateException.class,
                () -> selfInvocationTestService
                        .innerTransactional(user)
        );

        assertNotNull(user.getId());

        assertFalse(
                userRepository.existsById(user.getId())
        );
    }

    @Test
    void transactionalMethod_shouldBeBypassed_whenCalledBySelfInvocation() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = new UserEntity(
                "Self Invocation Test",
                "self-" + suffix + "@test.com"
        );

        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> selfInvocationTestService
                            .outerCall(user)
            );

            assertNotNull(user.getId());

            assertTrue(
                    userRepository.existsById(user.getId())
            );
        } finally {
            if (user.getId() != null &&
                    userRepository.existsById(user.getId())) {
                userRepository.deleteById(user.getId());
            }
        }
    }

    static class RollbackRuleTestService {

        private final UserRepository userRepository;

        RollbackRuleTestService(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Transactional
        public void saveUserThenThrowRuntime(UserEntity user) {
            userRepository.saveAndFlush(user);

            throw new IllegalStateException(
                    "Simulated runtime exception"
            );
        }

        @Transactional
        public void saveUserThenThrowChecked(UserEntity user)
                throws IOException {

            userRepository.saveAndFlush(user);

            throw new IOException(
                    "Simulated checked exception"
            );
        }

        @Transactional(rollbackFor = IOException.class)
        public void saveUserThenThrowCheckedWithRollback(UserEntity user)
                throws IOException {

            userRepository.saveAndFlush(user);

            throw new IOException(
                    "Simulated checked exception with rollback"
            );
        }
    }

    static class RequiredInnerService {

        private final UserRepository userRepository;

        RequiredInnerService(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Transactional
        public void saveUser(UserEntity user) {
            userRepository.saveAndFlush(user);
        }
    }

    static class RequiresNewInnerService {

        private final UserRepository userRepository;

        RequiresNewInnerService(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Transactional(
                propagation = Propagation.REQUIRES_NEW
        )
        public void saveUser(UserEntity user) {
            userRepository.saveAndFlush(user);
        }
    }

    static class PropagationOuterService {

        private final RequiredInnerService requiredInnerService;
        private final RequiresNewInnerService requiresNewInnerService;

        PropagationOuterService(
                RequiredInnerService requiredInnerService,
                RequiresNewInnerService requiresNewInnerService
        ) {
            this.requiredInnerService = requiredInnerService;
            this.requiresNewInnerService = requiresNewInnerService;
        }

        @Transactional
        public void saveWithRequiredThenFail(UserEntity user) {
            requiredInnerService.saveUser(user);

            throw new IllegalStateException(
                    "Outer transaction failed"
            );
        }

        @Transactional
        public void saveWithRequiresNewThenFail(UserEntity user) {
            requiresNewInnerService.saveUser(user);

            throw new IllegalStateException(
                    "Outer transaction failed"
            );
        }
    }

    static class SelfInvocationTestService {

        private final UserRepository userRepository;

        SelfInvocationTestService(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        public void outerCall(UserEntity user) {
            innerTransactional(user);
        }

        @Transactional
        public void innerTransactional(UserEntity user) {
            userRepository.saveAndFlush(user);

            throw new IllegalStateException(
                    "Simulated self-invocation failure"
            );
        }
    }
}