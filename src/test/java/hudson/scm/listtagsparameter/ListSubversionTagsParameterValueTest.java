package hudson.scm.listtagsparameter;

import org.junit.Test;
import org.jvnet.hudson.test.Bug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Created by schristou88 on 6/24/14.
 */
public class ListSubversionTagsParameterValueTest {
    String expectedName = "name";
    String expectedTag = "tag";
    String expectedTagsDir = "/tmp";
    /**
     * Since we are overriding the equals method, we should write a test unit.
     */
    @Test
    @Bug(18534)
    public void testEquality() {
        ListSubversionTagsParameterValue parameterValue = new ListSubversionTagsParameterValue(expectedName,
                expectedTag,
                expectedTagsDir);

        assertEquals(parameterValue, parameterValue);

        // When name is different
        ListSubversionTagsParameterValue otherParameterValue = new ListSubversionTagsParameterValue("different",
                expectedTag,
                expectedTagsDir);
        assertNotEquals("Two parameter values should NOT be equal if the only difference is the name.",
                parameterValue,
                otherParameterValue);

        // When tag is different
        otherParameterValue = new ListSubversionTagsParameterValue(expectedName,
                "tag2",
                expectedTagsDir);
        assertNotEquals("Two parameter values should NOT be equal if the difference is the tag.",
                parameterValue,
                otherParameterValue);

        // When tagsdir is different
        otherParameterValue = new ListSubversionTagsParameterValue(expectedName,
                expectedTag,
                "/tmp1");
        assertNotEquals("Two parameter values should NOT be equal if the difference is the tagsDir.",
                parameterValue,
                otherParameterValue);

        otherParameterValue = new ListSubversionTagsParameterValue(expectedName,
                expectedTag,
                expectedTagsDir);
        assertEquals("Two parameters with the same value should also be equal.",
                parameterValue,
                otherParameterValue);
    }

    /**
     * Since we are overriding the hashcode method, we should write a test unit.
     */
    @Test
    @Bug(18534)
    public void testHashCode() {
        ListSubversionTagsParameterValue parameterValue = new ListSubversionTagsParameterValue(expectedName,
                expectedTag,
                expectedTagsDir);

        assertEquals(parameterValue.hashCode(), parameterValue.hashCode());

        ListSubversionTagsParameterValue otherParameterValue = new ListSubversionTagsParameterValue(expectedName,
                expectedTag,
                expectedTagsDir);

        assertEquals(parameterValue.hashCode(), otherParameterValue.hashCode());
    }
}