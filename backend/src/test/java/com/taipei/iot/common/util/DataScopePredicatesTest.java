package com.taipei.iot.common.util;

import com.taipei.iot.dept.enums.DataScopeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataScopePredicates [common v2 F-15]")
class DataScopePredicatesTest {

	@ParameterizedTest(name = "isUnrestricted({0}) = {1}")
	@CsvSource({ "ALL,                    true", "THIS_LEVEL,             false", "THIS_LEVEL_AND_BELOW,   false" })
	void isUnrestricted(DataScopeEnum scope, boolean expected) {
		assertThat(DataScopePredicates.isUnrestricted(scope)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "isUnrestricted(null) = false")
	@NullSource
	void isUnrestrictedNull(DataScopeEnum scope) {
		assertThat(DataScopePredicates.isUnrestricted(scope)).isFalse();
	}

	@ParameterizedTest(name = "restrictsToOwner({0}) = {1}")
	@CsvSource({ "ALL,                    false", "THIS_LEVEL,             true", "THIS_LEVEL_AND_BELOW,   true" })
	void restrictsToOwner(DataScopeEnum scope, boolean expected) {
		assertThat(DataScopePredicates.restrictsToOwner(scope)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "restrictsToOwner(null) = true (保守視為受限)")
	@NullSource
	void restrictsToOwnerNullIsRestricted(DataScopeEnum scope) {
		assertThat(DataScopePredicates.restrictsToOwner(scope)).isTrue();
	}

	@ParameterizedTest(name = "restrictsToDeptScope({0}) = {1}")
	@CsvSource({ "ALL,                    false", "THIS_LEVEL,             true", "THIS_LEVEL_AND_BELOW,   true" })
	void restrictsToDeptScope(DataScopeEnum scope, boolean expected) {
		assertThat(DataScopePredicates.restrictsToDeptScope(scope)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "restrictsToDeptScope(null) = false")
	@NullSource
	void restrictsToDeptScopeNull(DataScopeEnum scope) {
		assertThat(DataScopePredicates.restrictsToDeptScope(scope)).isFalse();
	}

}
