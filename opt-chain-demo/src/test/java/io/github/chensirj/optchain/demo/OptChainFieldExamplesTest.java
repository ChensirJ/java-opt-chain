package io.github.chensirj.optchain.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field-chain twin of {@link OptChainExamplesTest}. Each link is a public field ({@code GETFIELD}),
 * including a null in the middle of {@code u?.address?.city?.name}.
 */
class OptChainFieldExamplesTest {

    private final OptChainExamples examples = new OptChainExamples();

    @Test
    void fullFieldChainReturnsValue() {
        FieldUser user = new FieldUser(new FieldAddress(new FieldCity("Shanghai")));
        assertEquals("Shanghai", examples.fieldCityName(user));
        assertEquals(8, examples.fieldNameLength(user));
    }

    @Test
    void nullAddressFieldReturnsNullWithoutNpe() {
        FieldUser user = new FieldUser(null);
        String city = assertDoesNotThrow(() -> examples.fieldCityName(user));
        assertNull(city);
        assertEquals(0, assertDoesNotThrow(() -> examples.fieldNameLength(user)));
    }

    @Test
    void nullCityFieldReturnsNullWithoutNpe() {
        FieldUser user = new FieldUser(new FieldAddress(null));
        String city = assertDoesNotThrow(() -> examples.fieldCityName(user));
        assertNull(city);
        assertEquals(0, assertDoesNotThrow(() -> examples.fieldNameLength(user)));
    }

    @Test
    void nullUserFieldReturnsNullWithoutNpe() {
        String city = assertDoesNotThrow(() -> examples.fieldCityName(null));
        assertNull(city);
        assertEquals(0, assertDoesNotThrow(() -> examples.fieldNameLength(null)));
    }

    @Test
    void fieldPrimitiveShortCircuitDefaults() {
        assertEquals(0, examples.fieldNameLength(null));
        assertFalse(examples.fieldPrimary(null));
        assertEquals(0L, examples.fieldCityId(null));
        assertEquals(0.0, examples.fieldCityLatitude(null), 0.0);

        FieldUser addressNull = new FieldUser(null);
        assertEquals(0, examples.fieldNameLength(addressNull));
        assertFalse(examples.fieldPrimary(addressNull));
        assertEquals(0L, examples.fieldCityId(addressNull));
        assertEquals(0.0, examples.fieldCityLatitude(addressNull), 0.0);

        FieldUser cityNull = new FieldUser(new FieldAddress(null));
        assertEquals(0, examples.fieldNameLength(cityNull));
        assertFalse(examples.fieldPrimary(cityNull));
        assertEquals(0L, examples.fieldCityId(cityNull));
        assertEquals(0.0, examples.fieldCityLatitude(cityNull), 0.0);
    }

    @Test
    void rootNullAndMidChainNullFieldBothShortCircuit() {
        FieldUser rootNull = null;
        FieldUser midAddressNull = new FieldUser(null);
        FieldUser midCityNull = new FieldUser(new FieldAddress(null, true));

        assertNull(examples.fieldCity(rootNull));
        assertNull(examples.fieldCity(midAddressNull));
        assertNull(examples.fieldCity(midCityNull));
        assertNull(examples.fieldCityName(rootNull));
        assertNull(examples.fieldCityName(midAddressNull));
        assertNull(examples.fieldCityName(midCityNull));

        assertEquals(0, examples.fieldNameLength(rootNull));
        assertEquals(0, examples.fieldNameLength(midAddressNull));
        assertEquals(0, examples.fieldNameLength(midCityNull));
        assertEquals(0L, examples.fieldCityId(rootNull));
        assertEquals(0L, examples.fieldCityId(midAddressNull));
        assertEquals(0L, examples.fieldCityId(midCityNull));

        // A field before the null still returns its real value.
        assertFalse(examples.fieldPrimary(rootNull));
        assertFalse(examples.fieldPrimary(midAddressNull));
        assertTrue(examples.fieldPrimary(midCityNull));
    }

    @Test
    void fieldReferenceChainIsNonNullWhenFullyPresent() {
        FieldCity city = new FieldCity("Shanghai", 21L, 31.5);
        FieldUser user = new FieldUser(new FieldAddress(city, true));

        assertSame(city, examples.fieldCity(user));
        assertEquals("Shanghai", examples.fieldCityName(user));
        assertEquals("Shanghai", examples.fieldCity(user).name);
        assertTrue(examples.fieldPrimary(user));
        assertEquals(21L, examples.fieldCityId(user));
        assertEquals(31.5, examples.fieldCityLatitude(user), 0.0);
        assertEquals(8, examples.fieldNameLength(user));
    }

    @Test
    void nullNameFieldShortCircuitsLengthButCityRemains() {
        FieldCity city = new FieldCity(null, 7L, 1.5);
        FieldUser user = new FieldUser(new FieldAddress(city, false));

        assertSame(city, examples.fieldCity(user));
        assertNull(examples.fieldCityName(user));
        assertEquals(0, examples.fieldNameLength(user));
        assertEquals(7L, examples.fieldCityId(user));
        assertEquals(1.5, examples.fieldCityLatitude(user), 0.0);
        assertFalse(examples.fieldPrimary(user));
    }
}
