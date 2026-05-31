package hudson.scm.listtagsparameter;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Created by schristou88 on 6/24/14.
 */
class ListSubversionTagsParameterValueTest {

    private static final String EXPECTED_NAME = "name";
    private static final String EXPECTED_TAG = "tag";
    private static final String EXPECTED_TAGS_DIR = "/tmp";

    /**
     * Since we are overriding the equals method, we should write a test unit.
     */
    @Test
    @Issue("JENKINS-18534")
    void testEquality() {
        ListSubversionTagsParameterValue parameterValue = new ListSubversionTagsParameterValue(EXPECTED_NAME,
                EXPECTED_TAG,
                EXPECTED_TAGS_DIR);

        assertEquals(parameterValue, parameterValue);

        // When name is different
        ListSubversionTagsParameterValue otherParameterValue = new ListSubversionTagsParameterValue("different",
                EXPECTED_TAG,
                EXPECTED_TAGS_DIR);
        assertNotEquals(parameterValue,
                otherParameterValue,
                "Two parameter values should NOT be equal if the only difference is the name.");

        // When tag is different
        otherParameterValue = new ListSubversionTagsParameterValue(EXPECTED_NAME,
                "tag2",
                EXPECTED_TAGS_DIR);
        assertNotEquals(parameterValue,
                otherParameterValue,
                "Two parameter values should NOT be equal if the difference is the tag.");

        // When tagsdir is different
        otherParameterValue = new ListSubversionTagsParameterValue(EXPECTED_NAME,
                EXPECTED_TAG,
                "/tmp1");
        assertNotEquals(parameterValue,
                otherParameterValue,
                "Two parameter values should NOT be equal if the difference is the tagsDir.");

        otherParameterValue = new ListSubversionTagsParameterValue(EXPECTED_NAME,
                EXPECTED_TAG,
                EXPECTED_TAGS_DIR);
        assertEquals(parameterValue,
                otherParameterValue,
                "Two parameters with the same value should also be equal.");
    }

    /**
     * Since we are overriding the hashcode method, we should write a test unit.
     */
    @Test
    @Issue("JENKINS-18534")
    void testHashCode() {
        ListSubversionTagsParameterValue parameterValue = new ListSubversionTagsParameterValue(EXPECTED_NAME,
                EXPECTED_TAG,
                EXPECTED_TAGS_DIR);

        assertEquals(parameterValue.hashCode(), parameterValue.hashCode());

        ListSubversionTagsParameterValue otherParameterValue = new ListSubversionTagsParameterValue(EXPECTED_NAME,
                EXPECTED_TAG,
                EXPECTED_TAGS_DIR);

        assertEquals(parameterValue.hashCode(), otherParameterValue.hashCode());
    }
}