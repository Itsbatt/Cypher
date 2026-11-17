import org.yaml.snakeyaml.Yaml;
import java.io.FileInputStream;
import java.util.Map;

public final class YamlCheck {
    public static void main(String[] args) throws Exception {
        int failures = 0;
        for (String path : args) {
            try (FileInputStream input = new FileInputStream(path)) {
                Object doc = new Yaml().load(input);
                if (!(doc instanceof Map)) {
                    System.out.println(path + " -> not a mapping");
                    failures++;
                    continue;
                }
                Map<?, ?> root = (Map<?, ?>) doc;
                Object jobs = root.get("jobs");
                System.out.println(path + " -> OK, jobs=" + (jobs instanceof Map ? ((Map<?, ?>) jobs).keySet() : jobs));
            } catch (Exception error) {
                System.out.println(path + " -> ERROR " + error.getMessage());
                failures++;
            }
        }
        System.exit(failures == 0 ? 0 : 1);
    }
}
