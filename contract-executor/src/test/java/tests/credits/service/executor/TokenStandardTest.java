package tests.credits.service.executor;

import com.credits.scapi.v2.ObservableMap;
import com.credits.scapi.v2.WalletAddress;
import com.credits.service.contract.SmartContractAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.credits.scapi.misc.TokenStandardId.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TokenStandardTest {

    abstract static class BasicV0 implements com.credits.scapi.v0.BasicStandard { }

    abstract static class ExtensionV0 implements com.credits.scapi.v0.ExtensionStandard { }

    abstract static class BasicV1 implements com.credits.scapi.v1.BasicTokenStandard { }

    abstract static class ExtensionV1 implements com.credits.scapi.v1.ExtensionTokenStandard { }

    abstract static class BasicV2 implements com.credits.scapi.v2.BasicTokenStandard { }

    abstract static class ExtensionV2 implements com.credits.scapi.v2.ExtensionTokenStandard { }

    abstract static class FromBaseClass extends ExtensionV1 { }

    interface CustomToken extends com.credits.scapi.v1.BasicTokenStandard {
        BigDecimal mint(String to, BigDecimal amount);
    }

    abstract static class FromOwnInterface implements CustomToken { }

    // declares both standards itself: the first in TokenStandardId's order, as build 1518
    abstract static class BothDeclared implements com.credits.scapi.v0.ExtensionStandard, com.credits.scapi.v0.BasicStandard { }

    static class NotAToken {
        public ObservableMap<WalletAddress, BigDecimal> getTokenBalances() {
            return null;
        }
    }

    private static long standardOf(Class<?> c) {
        return SmartContractAnalyzer.defineTokenStandard(c);
    }

    @Test
    @DisplayName("token standards declared by the class keep the ids of build 1518")
    void declaredStandards() {
        assertThat(standardOf(BasicV0.class), is(BASIC_STANDARD.getId()));
        assertThat(standardOf(ExtensionV0.class), is(EXTENSION_STANDARD.getId()));
        assertThat(standardOf(BasicV1.class), is(BASIC_TOKEN_STANDARD.getId()));
        assertThat(standardOf(ExtensionV1.class), is(EXTENSION_TOKEN_STANDARD.getId()));
        assertThat(standardOf(BothDeclared.class), is(BASIC_STANDARD.getId()));
        assertThat(standardOf(NotAToken.class), is(NOT_A_TOKEN.getId()));
    }

    @Test
    @DisplayName("v2 standards and inherited standards are tokens")
    void inheritedStandards() {
        assertThat(standardOf(BasicV2.class), is(BASIC_TOKEN_STANDARD.getId()));
        assertThat(standardOf(ExtensionV2.class), is(EXTENSION_TOKEN_STANDARD.getId()));
        assertThat(standardOf(FromBaseClass.class), is(EXTENSION_TOKEN_STANDARD.getId()));
        assertThat(standardOf(FromOwnInterface.class), is(BASIC_TOKEN_STANDARD.getId()));
    }
}
