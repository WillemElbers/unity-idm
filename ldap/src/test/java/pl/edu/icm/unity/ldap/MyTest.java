package pl.edu.icm.unity.ldap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 *
 * @author wilelb
 */
public class MyTest {

    @Test
    public void test() {
        Pattern p = Pattern.compile("cn=([^,]+)(,.+)?");
                
        Matcher m1 = p.matcher("cn=test1,ou=system");
        Matcher m2 = p.matcher("cn=test2");

        assertThat(m1.find(), is(equalTo(true)));
        String user1 = m1.group(1);
        assertThat(user1, is(not(nullValue())));
        assertThat(user1, is(equalTo("test1")));
        
        assertThat(m2.find(), is(equalTo(true)));
        String user2 = m2.group(1);
        assertThat(user2, is(not(nullValue())));
        assertThat(user2, is(equalTo("test2")));
    }
}
