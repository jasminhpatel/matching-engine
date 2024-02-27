package com.solfini;

import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Properties;

public class loadingTest {
  public static void main(String[] args) throws IOException, InterruptedException {
    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

    // get the jvm's input arguments as a list of strings
    List<String> listOfArguments = runtimeMxBean.getInputArguments();

   for (String s : listOfArguments) {
     System.out.println(s);
   }

    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("INSTANCE_ID", "injector");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    PropertyReader.initialize(new FileInputStream(new File("/Users/rohanw/Documents/XinoTech/SolfiniOrg/solfini-matching-engine/server/config.properties")), properties);

    SnapLoader loader = new SnapLoader(1700739952644787958L);
    loader.load();
    Thread.sleep(5000);
  }
}
