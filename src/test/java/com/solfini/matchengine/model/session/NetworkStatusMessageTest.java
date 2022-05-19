package com.solfini.matchengine.model.session;

import java.io.IOException;
import java.util.Properties;
import com.solfini.matchengine.message.session.NetworkStatusMessage;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import org.slf4j.event.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NetworkStatusMessageTest extends ModelTest {
    final void configure() {
        LogLevel.setLevel(Level.TRACE);

        Properties properties = new Properties();
        PoolSize.minimize(properties);
        properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "4194304");
        properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "4028");
        properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
        properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "65536");
        properties.setProperty("POSITION_POOL_START_CAPACITY", "32768");
        properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "65536");
        properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "32768");
        properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "65536");
        properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "32768");
        properties.setProperty("NUM_ENCODER_THREADS", "0");
        properties.setProperty("NUM_DECODER_THREADS", "0");
        properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
        properties.setProperty("KAFKA.CONSUMER.key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        properties.setProperty("KAFKA.CONSUMER.value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        try {
            PropertyReader.initialize(null, properties);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Before
    public final void before() {
        configure();
        clearQueues();
    }

    @After
    public final void after() {
        clearQueues();
    }

    @Test
    public void testNetworkStatusMessage() {
        NetworkStatusMessage message = new NetworkStatusMessage(1, 2, 3, "4");

        message.onMatcher();

        expectMessage("NetworkStatusMessage" ,"requestId=1, responseId=2, orderSequenceNumber=3");
        assertMessages();

        expectOutput("NetworkStatusMessage" ,"requestId=1, responseId=2, orderSequenceNumber=3");
        assertOutputMessages();
    }
}
