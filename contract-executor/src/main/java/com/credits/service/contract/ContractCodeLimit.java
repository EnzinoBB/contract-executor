package com.credits.service.contract;

import exception.ContractExecutorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;

/**
 * Runs contract code the executor calls outside a contract method's time limit: the getName() and
 * getSymbol() of the reserved token name check, and the readObject()/writeObject()/hashCode() a
 * contract's classes run while its state is deserialized or serialized.
 *
 * Build 1518 runs that code on the Thrift thread with no limit, so code that never returns keeps a
 * CPU busy for the life of the process, and the call never gets an answer. Here it runs on a thread
 * of its own: what it returns or throws is passed on unchanged, so every call that ends gives the
 * answer build 1518 gives. One that is still running after the limit is stopped, and the answer is
 * held back for HOLD_MS before a failure is returned: the node stops waiting long before (its
 * executor_receive_timeout, 4 s by default), as it does with build 1518, which never answers.
 */
public final class ContractCodeLimit {
    private static final Logger logger = LoggerFactory.getLogger(ContractCodeLimit.class);

    // a node's executor_receive_timeout is below this
    static final long HOLD_MS = Long.getLong("contract.executor.unbounded.code.hold.ms", 600_000L);
    // contract code outside a method gets at least this long, a method's limit if that is longer
    static final long MIN_LIMIT_MS = Long.getLong("contract.executor.unbounded.code.limit.ms", 120_000L);

    private ContractCodeLimit() {
    }

    public static <R> R run(String what, long executionTimeMs, Callable<R> block) {
        final long limit = Math.max(executionTimeMs, MIN_LIMIT_MS);
        final var task = new FutureTask<>(block);
        final var thread = new Thread(task, Thread.currentThread().getName() + " " + what);
        thread.setDaemon(true);
        thread.start();
        try {
            return task.get(limit, MILLISECONDS);
        } catch (ExecutionException e) {
            return rethrow(e.getCause());
        } catch (TimeoutException e) {
            stop(thread);
            logger.warn("{}: contract code still running after {} ms, stopped; the answer is held back {} ms", what, limit, HOLD_MS);
            hold();
            throw new ContractExecutorException(what + ": contract code did not end within " + limit + " ms");
        } catch (InterruptedException e) {
            stop(thread);
            Thread.currentThread().interrupt();
            throw new ContractExecutorException(what + ": interrupted");
        }
    }

    @SuppressWarnings("deprecation")
    private static void stop(Thread thread) {
        thread.interrupt();
        try {
            thread.join(3);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            thread.stop();
        }
    }

    private static void hold() {
        try {
            Thread.sleep(HOLD_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
