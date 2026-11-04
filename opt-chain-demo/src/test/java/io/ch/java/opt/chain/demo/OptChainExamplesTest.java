package io.ch.java.opt.chain.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void primitiveShortCircuitDefaults() {
        assertEquals(0, examples.nameLength(null));
        assertFalse(examples.primaryAddress(null));
        assertEquals(0L, examples.cityId(null));
        assertEquals(0.0, examples.cityLatitude(null), 0.0);

        User addressNull = new User(null);
        assertEquals(0, examples.nameLength(addressNull));
        assertFalse(examples.primaryAddress(addressNull));
        assertEquals(0L, examples.cityId(addressNull));
        assertEquals(0.0, examples.cityLatitude(addressNull), 0.0);

        User cityNull = new User(new Address(null));
        assertEquals(0, examples.nameLength(cityNull));
        assertFalse(examples.primaryAddress(cityNull));
        assertEquals(0L, examples.cityId(cityNull));
        assertEquals(0.0, examples.cityLatitude(cityNull), 0.0);
    }

    @Test
    void rootNullAndMidChainNullBothShortCircuit() {
        User rootNull = null;
        User midAddressNull = new User(null);
        User midCityNull = new User(new Address(null, true));

        assertNull(examples.city(rootNull));
        assertNull(examples.city(midAddressNull));
        assertNull(examples.city(midCityNull));
        assertNull(examples.cityOf(rootNull));
        assertNull(examples.cityOf(midAddressNull));
        assertNull(examples.cityOf(midCityNull));

        assertEquals(0, examples.nameLength(rootNull));
        assertEquals(0, examples.nameLength(midAddressNull));
        assertEquals(0, examples.nameLength(midCityNull));
        assertEquals(0L, examples.cityId(rootNull));
        assertEquals(0L, examples.cityId(midAddressNull));
        assertEquals(0L, examples.cityId(midCityNull));

        // A link before the null still returns its real value.
        assertFalse(examples.primaryAddress(rootNull));
        assertFalse(examples.primaryAddress(midAddressNull));
        assertTrue(examples.primaryAddress(midCityNull));
    }

    @Test
    void referenceChainIsNonNullWhenFullyPresent() {
        City city = new City("Shanghai", 21L, 31.5);
        User user = new User(new Address(city, true));

        assertSame(city, examples.city(user));
        assertEquals("Shanghai", examples.cityOf(user));
        assertEquals("Shanghai", examples.city(user).getName());
        assertTrue(examples.primaryAddress(user));
        assertEquals(21L, examples.cityId(user));
        assertEquals(31.5, examples.cityLatitude(user), 0.0);
        assertEquals(8, examples.nameLength(user));
    }

    @Test
    void nullNameShortCircuitsLengthButCityRemains() {
        City city = new City(null, 7L, 1.5);
        User user = new User(new Address(city, false));

        assertSame(city, examples.city(user));
        assertNull(examples.cityOf(user));
        assertEquals(0, examples.nameLength(user));
        assertEquals(7L, examples.cityId(user));
        assertEquals(1.5, examples.cityLatitude(user), 0.0);
        assertFalse(examples.primaryAddress(user));
    }
}
