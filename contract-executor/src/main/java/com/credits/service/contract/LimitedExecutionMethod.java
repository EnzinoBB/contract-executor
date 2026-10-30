package com.credits.service.contract;

import com.credits.general.thrift.generated.Variant;
import com.credits.thrift.ClientConnection;
import com.credits.utils.StopWatch;
import org.apache.thrift.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.session.DeployContractSession;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static pojo.SmartContractConstants.forgetSmartContractConstants;
import static pojo.SmartContractConstants.initSmartContractConstants;

class LimitedExecutionMethod<R> {
    private static final Logger logger = LoggerFactory.getLogger(LimitedExecutionMethod.class);
    // how often a running method looks whether its node still waits
    private static final long NODE_CHECK_MS = 250;
    protected DeployContractSession session;
    private StopWatch stopWatch;
    private Throwable exception;
    private long threadId;

    LimitedExecutionMethod(DeployContractSession session) {
        this.session = session;
    }

    protected R runForLimitTime(Callable<R> block) {
        final var task = new FutureTask<>(() -> {
            stopWatch.start();
            final R res;
            try {
                res = block.call();
            } finally {
                stopWatch.stop();
            }
            return res;
        });
        final var limitedTimeThread = new Thread(task);
        threadId = limitedTimeThread.getId();
        stopWatch = new StopWatch(limitedTimeThread);
        exception = null;
        R result = null;
        try {
            limitedTimeThread.setName(session.contractAddress);
            initSmartContractConstants(limitedTimeThread.getId(), session);
            limitedTimeThread.start();
            result = awaitWhileNodeWaits(task);

        } catch (TimeoutException e) {
            limitedTimeThread.interrupt();

            if (limitedTimeThread.isAlive()) {
                try {
                    sleep(3);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                if (limitedTimeThread.isAlive()) {
                    limitedTimeThread.stop();
                    stopWatch.stop();
                    exception = e;
                }
            }

            if (task.isDone()) {
                try {
                    result = task.get();
                } catch (Throwable ex) {
                    exception = ex;
                }
            }
        } catch (Throwable e) {
            exception = e;
        } finally {
            // only a deploy's constructor takes the constants: drop them after every other call too
            forgetSmartContractConstants(limitedTimeThread.getId());
        }
        return result;
    }

    // the result within the execution time; a TimeoutException as at the limit when the node has
    // closed the connection before (it gave up on the answer, which could not be delivered)
    private R awaitWhileNodeWaits(FutureTask<R> task) throws Exception {
        final long deadline = System.nanoTime() + MILLISECONDS.toNanos(session.executionTime);
        while (true) {
            final long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (left <= 0) {
                throw new TimeoutException();
            }
            try {
                return task.get(Math.min(left, NODE_CHECK_MS), MILLISECONDS);
            } catch (TimeoutException notYet) {
                if (ClientConnection.closedByClient()) {
                    logger.warn("{}: the node closed the connection, the call is stopped", session.contractAddress);
                    throw notYet;
                }
            }
        }
    }

    protected R runIntoCurrentThread(Callable<R> block){
        R result = null;
        threadId = Thread.currentThread().getId();
        try {
            result = block.call();
        }catch (Throwable e){
            exception = e;
        }
        return result;
    }

    public long spentCpuTime() {
        return stopWatch != null ? stopWatch.getTime() : 0L;
    }

    @Nullable
    private Throwable getExceptionOrNull() {
        return exception;
    }

    protected MethodResult prepareResult(Variant returnValue) {
        return getExceptionOrNull() == null
                ? new MethodResult(returnValue, spentCpuTime(), threadId)
                : new MethodResult(getExceptionOrNull(), spentCpuTime(), threadId);
    }
}
