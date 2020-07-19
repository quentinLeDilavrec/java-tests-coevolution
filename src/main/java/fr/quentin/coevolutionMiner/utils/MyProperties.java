package fr.quentin.coevolutionMiner.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

public final class MyProperties {
    private static Properties prop = null;

    private MyProperties() {}
 
	public static Properties getPropValues() {
        if (prop != null) {
            return prop;
        }
        prop = new Properties();
		String propFileName = "config.properties";
		try (InputStream inputStream = MyProperties.class.getClassLoader().getResourceAsStream(propFileName);) {
 
			if (inputStream != null) {
				prop.load(inputStream);
			} else {
				throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
			}
			return prop;
		} catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return prop;
        }
	}
}