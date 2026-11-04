package io.github.chensirj.optchain.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyOptChainExamplesTest {

    private final LegacyOptChainExamples examples = new LegacyOptChainExamples();

    @Test
    void handWrittenAnnotationStillNullChecksEveryInstanceAccess() {
        User shanghai = new User(new Address(new City("Shanghai")));
        assertEquals("Shanghai", examples.cityOf(shanghai));
        assertEquals(8, examples.nameLength(shanghai));

        assertNull(examples.cityOf(null));
        assertNull(examples.cityOf(new User(null)));
        assertNull(examples.cityOf(new User(new Address(null))));
        assertEquals(0, examples.nameLength(null));
        assertEquals(0, examples.nameLength(new User(new Address(new City(null)))));
    }
}
