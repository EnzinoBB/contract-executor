package com.credits.service.node.apiexec;

import com.credits.client.executor.thrift.generated.apiexec.*;
import com.credits.client.node.thrift.generated.Transaction;
import com.credits.client.node.thrift.generated.WalletBalanceGetResult;
import com.credits.client.node.thrift.generated.WalletIdGetResult;
import com.credits.exception.ApiClientException;
import com.credits.general.thrift.ThriftClientPool;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class NodeThriftApiExecClient implements NodeThriftApiExec {
    private final ThriftClientPool<APIEXEC.Client> pool;
    private final static Logger logger = LoggerFactory.getLogger(NodeThriftApiExecClient.class);

    public NodeThriftApiExecClient(String apiServerHost, Integer apiServerPort) {
        pool = new ThriftClientPool<>(APIEXEC.Client::new, apiServerHost, apiServerPort);

        logger.info("Connection to node on address {}:{}", apiServerHost, apiServerPort);
    }

    @Override
    public GetSeedResult getSeed(long accessId) throws ApiClientException {
        return callThrift(client -> client.GetSeed(accessId), true);
    }

    @Override
    public SmartContractGetResult getSmartContractBinary(long accessId, byte[] address) throws ApiClientException {
        return callThrift(client -> client.SmartContractGet(accessId, ByteBuffer.wrap(address)), true);
    }


    @Override
    public SendTransactionResult sendTransaction(long accessId, Transaction transaction) throws ApiClientException {
        return callThrift(client -> client.SendTransaction(accessId, transaction), false);
    }

    @Override
    public WalletIdGetResult getWalletId(long accessId, byte[] address) throws ApiClientException {
        return callThrift(client -> client.WalletIdGet(accessId, ByteBuffer.wrap(address)), true);
    }

    @Override
    public WalletBalanceGetResult getBalance(byte[] address) throws ApiClientException {
        return callThrift(client -> client.WalletBalanceGet(ByteBuffer.wrap(address)), true);
    }

    @Override
    public GetDateTimeResult getDateTime(long accessId) throws ApiClientException {
        return callThrift(client -> client.GetDateTime(accessId), true);
    }

    // A pooled connection goes back to the pool on every outcome: an exception other than TException
    // (or a Thread.stop of the calling contract) used to leak it. A read-only call is retried once on a
    // new connection when the pooled one was dead, typically after a node restart.
    private <R> R callThrift(Function<R> method, boolean retryDroppedConnection) throws ApiClientException {
        for (int attempt = 0; ; attempt++) {
            final APIEXEC.Client client = pool.getResource();
            boolean healthy = false;
            try {
                final R res = method.apply(client);
                healthy = true;
                return res;
            } catch (TTransportException e) {
                if (!retryDroppedConnection || attempt > 0) {
                    throw new ApiClientException(e);
                }
                logger.debug("connection to node dropped, retrying once: {}", e.getMessage());
            } catch (TException e) {
                throw new ApiClientException(e);
            } finally {
                if (healthy) {
                    pool.returnResource(client);
                } else {
                    pool.returnBrokenResource(client);
                }
            }
        }
    }

    private interface Function<R> {
        R apply(APIEXEC.Client client) throws TException;
    }
}