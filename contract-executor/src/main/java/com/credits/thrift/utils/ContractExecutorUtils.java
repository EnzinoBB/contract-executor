package com.credits.thrift.utils;

import com.credits.exception.IncompatibleVersionException;
import com.credits.general.classload.ByteCodeContractClassLoader;
import com.credits.general.pojo.ByteCodeObjectData;
import exception.ContractExecutorException;

import java.util.ArrayList;
import java.util.List;
import static com.credits.ApplicationProperties.API_VERSION;
import com.credits.secure.PermissionsManager;

public class ContractExecutorUtils {

    // every class of a contract runs with the contract's permissions from the moment it is defined
    private static final PermissionsManager CONTRACT_PERMISSIONS = new PermissionsManager();

    // only a class the contract loader defined itself: loadClass returns an already loaded class when
    // the name is taken (the executor's own or the JDK's), and confining that would confine it
    // process-wide for every later call
    private static Class<?> confined(Class<?> loaded, ByteCodeContractClassLoader classLoader) {
        if (loaded.getClassLoader() == classLoader) {
            CONTRACT_PERMISSIONS.dropSmartContractRights(loaded);
        }
        return loaded;
    }

    public static Class<?> findRootClass(List<Class<?>> classes) {
        return classes.stream()
                .filter(clazz -> !clazz.getName().contains("$"))
                .findAny()
                .orElseThrow(() -> new ContractExecutorException("executor class not compiled"));
    }

    public static void loadClassesToClassloader(List<ByteCodeObjectData> objectDataList, ByteCodeContractClassLoader classLoader) {
        objectDataList.forEach(o -> confined(classLoader.loadClass(o.getName(), o.getByteCode()), classLoader));
    }

    public static List<Class<?>> compileSmartContractByteCode(List<ByteCodeObjectData> smartContractByteCodeData,
                                                              ByteCodeContractClassLoader byteCodeContractClassLoader) {
        
        List<Class<?>> compiledClasses = new ArrayList<>(smartContractByteCodeData.size());
        for (ByteCodeObjectData compilationUnit : smartContractByteCodeData) {
            compiledClasses.add(confined(byteCodeContractClassLoader.loadClass(compilationUnit.getName(), compilationUnit.getByteCode()),
                                          byteCodeContractClassLoader));
        }
        return compiledClasses;
    }

    public static void validateVersion(short version) {
        if (version != API_VERSION) {
            throw new IncompatibleVersionException(String.format("Invalid version %s, %s expected", version, API_VERSION));
        }
    }
}
