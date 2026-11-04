package io.github.chensirj.optchain.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptChainPreciseExamplesTest {

    private final OptChainPreciseExamples examples = new OptChainPreciseExamples();
    private final User shanghai = new User(new Address(new City("Shanghai")));

    @Test
    void plainDotStillThrowsInsideWovenMethod() {
        assertThrows(NullPointerException.class, () -> examples.plainCity(null));
        assertThrows(NullPointerException.class, () -> examples.plainCity(new User(null)));
        assertEquals("Shanghai", examples.plainCity(shanghai));
    }

    @Test
    void optionalSitesShortCircuit() {
        assertNull(examples.optionalCity(null));
        assertNull(examples.optionalCity(new User(null)));
        assertNull(examples.optionalCity(new User(new Address(null))));
        assertEquals("Shanghai", examples.optionalCity(shanghai));
    }

    @Test
    void optionalThenPlainDotStillThrows() {
        assertThrows(NullPointerException.class, () -> examples.optionalThenPlain(null));
        assertThrows(NullPointerException.class, () -> examples.optionalThenPlain(new User(null)));
        assertThrows(NullPointerException.class, () -> examples.optionalThenPlain(new User(new Address(null))));
        assertEquals("Shanghai", examples.optionalThenPlain(shanghai));
    }

    @Test
    void multipleChainsOnOneLine() {
        assertEquals("null:null", examples.twoCities(null, null));
        assertEquals("Shanghai:null", examples.twoCities(shanghai, null));
        assertEquals("Shanghai:Oslo", examples.twoCities(shanghai, new User(new Address(new City("Oslo")))));
    }

    @Test
    void mixedPlainAndOptionalOnOneLine() {
        assertThrows(NullPointerException.class, () -> examples.mixedLine(null, shanghai));
        assertEquals("Shanghai:null", examples.mixedLine(shanghai, null));
        assertEquals("Shanghai:Oslo", examples.mixedLine(shanghai, new User(new Address(new City("Oslo")))));
    }

    @Test
    void staticLambdaAnonymousAndInner() {
        assertNull(OptChainPreciseExamples.staticCity(null));
        assertEquals("Shanghai", OptChainPreciseExamples.staticCity(shanghai));
        assertNull(OptChainPreciseExamples.fromLambda(null));
        assertEquals("Shanghai", OptChainPreciseExamples.fromLambda(shanghai));
        assertNull(examples.fromAnonymous(null));
        assertEquals("Shanghai", examples.fromAnonymous(shanghai));
        assertNull(examples.fromInner(null));
        assertEquals("Shanghai", examples.fromInner(shanghai));
    }

    @Test
    void constructorFieldInitializerAndStaticInitializer() {
        assertEquals("FieldCity", new OptChainInitExamples(null).fieldCaptured);
        assertEquals("StaticCity", OptChainInitExamples.STATIC_OK);
        assertNull(OptChainInitExamples.STATIC_MISSING);
        assertNull(new OptChainInitExamples(null).ctorBody);
        assertEquals("Shanghai", new OptChainInitExamples(shanghai).ctorBody);

        OptChainInitExamples examples = new OptChainInitExamples(shanghai);
        assertThrows(NullPointerException.class, () -> examples.plainCtor(null));
        assertEquals("Shanghai", examples.plainCtor(shanghai));
    }
}
