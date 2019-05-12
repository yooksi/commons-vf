/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package commons;

import io.yooksi.commons.define.PositiveRange;
import io.yooksi.commons.validator.BeanValidator;
import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryTest {

    @PositiveRange(max=100)
    public final int positiveValue = 25;

    @PositiveRange(max=10)
    public final int rangeValue = 25;

    @PositiveRange(max=1)
    public final int negativeValue = -25;

    @Test
    public static void testLibraryAnnotations() {

        LibraryTest lt = new LibraryTest();
        BeanValidator.validate(lt);
    }
}
