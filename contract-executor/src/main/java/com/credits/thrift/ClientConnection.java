package com.credits.thrift;

import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;

/**
 * The connection of the request the current Thrift thread is answering, to tell whether the node
 * still waits for the answer.
 *
 * The node waits executor_receive_timeout (4 s by default) for an answer, then records the call
 * as timed out and closes the connection; the executor gives a contract method the execution
 * time the node sends (60 s), so a method that runs longer than the node waits used to go on,
 * holding a CPU, for an answer nobody reads. A method whose node has closed the connection is
 * stopped: the answer could not be delivered, so what the node records does not change.
 */
public final class ClientConnection {
    private static final Logger logger = LoggerFactory.getLogger(ClientConnection.class);
    private static final ThreadLocal<TTransport> current = new ThreadLocal<>();
    private static final Field inputStreamField = inputStreamField();

    private ClientConnection() {
    }

    // the processor of the server: every request knows its connection
    public static TProcessor tracking(TProcessor processor) {
        return new TProcessor() {
            @Override
            public boolean process(TProtocol in, TProtocol out) throws TException {
                current.set(in.getTransport());
                try {
                    return processor.process(in, out);
                } finally {
                    current.remove();
                }
            }
        };
    }

    /**
     * True only when the connection of the current request is known to be closed by the node
     * (end of stream). Unknown connections, data waiting and errors count as open.
     */
    public static boolean closedByClient() {
        final TTransport transport = current.get();
        if (!(transport instanceof TSocket) || inputStreamField == null) {
            return false;
        }
        final TSocket socket = (TSocket) transport;
        try {
            final InputStream in = (InputStream) inputStreamField.get(socket);
            if (!(in instanceof BufferedInputStream) || in.available() > 0) {
                return false;
            }
            final int timeout = socket.getSocket().getSoTimeout();
            socket.getSocket().setSoTimeout(1);
            try {
                in.mark(1);
                if (in.read() < 0) {
                    return true;
                }
                in.reset();  // not expected while a request is answered: left for Thrift to read
                return false;
            } catch (SocketTimeoutException open) {
                return false;
            } finally {
                socket.getSocket().setSoTimeout(timeout);
            }
        } catch (Exception e) {
            logger.debug("connection state unknown: {}", e.toString());
            return false;
        }
    }

    private static Field inputStreamField() {
        try {
            final Field field = TIOStreamTransport.class.getDeclaredField("inputStream_");
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            logger.warn("calls whose node stopped waiting run to their limit: {}", e.toString());
            return null;
        }
    }
}
