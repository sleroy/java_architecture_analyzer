import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegexValidationTest {
    public static void main(String[] args) {
        String[] patterns = {
            ".*-service\\.xml",     // MBean service files
            ".*-ds\\.xml",          // DataSource configuration files  
            "jboss-.*\\.xml",       // Any JBoss-prefixed XML files
            ".*-jboss\\.xml"        // Any JBoss-suffixed XML files
        };
        
        System.out.println("Validating JBoss configuration regex patterns:");
        
        for (String pattern : patterns) {
            try {
                Pattern.compile(pattern);
                System.out.println("✓ VALID: " + pattern);
            } catch (PatternSyntaxException e) {
                System.out.println("✗ INVALID: " + pattern + " - " + e.getMessage());
            }
        }
        
        // Test some example filenames
        System.out.println("\nTesting example filenames:");
        String[] testFiles = {
            "my-service.xml",
            "database-ds.xml", 
            "jboss-web.xml",
            "ejb-jboss.xml"
        };
        
        for (String testFile : testFiles) {
            System.out.println("\nTesting: " + testFile);
            for (String pattern : patterns) {
                boolean matches = testFile.matches(pattern);
                System.out.println("  " + pattern + " -> " + matches);
            }
        }
    }
}