package io.ch.java.opt.chain.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OptChainExamplesTest {

    private final OptChainExamples examples = new OptChainExamples();

    @Test
    void fullChainReturnsValue() {
        User user = new User(new Address(new City("Shanghai")));
        assertEquals("Shanghai", examples.cityOf(user));
        assertEquals(8, examples.nameLength(user));
    }

    @Test
    void nullAddressReturnsNullWithoutNpe() {
        User user = new User(null);
        String city = assertDoesNotThrow(() -> examples.cityOf(user));
        assertNull(city);
        assertEquals(0, assertDoesNotThrow(() -> examples.nameLength(user)));
    }

    @Test
    void nullCityReturnsNullWithoutNpe() {
        User user = new User(new Address(null));
        String city = assertDoesNotThrow(() -> examples.cityOf(user));
        assertNull(city);
        assertEquals(0, assertDoesNotThrow(() -> examples.nameLength(user)));
    }

    @Test
    void nullUserReturnsNullWithoutNpe() {
        String city = assertDoesNotThrow(() -> examples.cityOf(null));
        assertNull(city);
        assertEquals(0, assertDoesNotThrow(() -> examples.nameLength(null)));
    }
}
