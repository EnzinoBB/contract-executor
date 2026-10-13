package pojo;

import pojo.session.DeployContractSession;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SmartContractConstants {
    // a strong map: nothing else references the boxed thread id, so a weak key could be collected
    // between init and the contract constructor, failing that deploy on this node only
    private static final Map<Long, SmartContractConstants> sessions = new ConcurrentHashMap<>();
    public final String initiator;
    public final String contractAddress;
    public final long accessId;
    public final Map<String, ExternalSmartContract> usedContracts;

    private SmartContractConstants(String initiator, String contractAddress, long accessId, Map<String, ExternalSmartContract> usedContracts) {
        this.initiator = initiator;
        this.contractAddress = contractAddress;
        this.accessId = accessId;
        this.usedContracts = usedContracts;
    }

    public static void initSmartContractConstants(long threadId, DeployContractSession session) {
        sessions.put(threadId,
                     new SmartContractConstants(session.initiatorAddress, session.contractAddress, session.accessId, session.usedContracts));
    }


    public static void forgetSmartContractConstants(long threadId) {
        sessions.remove(threadId);
    }

    public static SmartContractConstants getSessionSmartContractConstants(long threadId) {
        return Objects.requireNonNull(sessions.remove(threadId), "contract constants is null. Thread id=" + threadId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SmartContractConstants that = (SmartContractConstants) o;

        if (accessId != that.accessId) return false;
        if (initiator != null ? !initiator.equals(that.initiator) : that.initiator != null) return false;
        return contractAddress != null ? contractAddress.equals(that.contractAddress) : that.contractAddress == null;
    }

    @Override
    public int hashCode() {
        int result = initiator != null ? initiator.hashCode() : 0;
        result = 31 * result + (contractAddress != null ? contractAddress.hashCode() : 0);
        result = 31 * result + (int) (accessId ^ (accessId >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "SmartContractConstants{" +
                "initiator='" + initiator + '\'' +
                ", contractAddress='" + contractAddress + '\'' +
                ", accessId=" + accessId +
                '}';
    }
}
