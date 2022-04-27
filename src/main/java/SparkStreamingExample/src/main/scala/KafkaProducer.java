package SparkStreamingExample.src.main.scala;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Properties;

public class KafkaProducer {

    //Directorio de datos
    private static String PATH = "" ;

    //Kafka Producer params
    private final static String TOPIC = "test";
    private final static String CLIENT_ID = "KafkaProducer01";
    private final static int NUM_PARTITIONS = 1;


    public static void main(String... args) throws Exception {

        PATH = args[0];

        /* Configuración del productor */
        Properties props = new Properties();
        props.put("bootstrap.servers","localhost:9092");
        props.put("key.serializer", LongSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("client.id", CLIENT_ID);
        props.put("num.partitions", NUM_PARTITIONS);
        final Producer<Long,String> producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);

        BufferedReader br = null;
        String line;
        long time = System.currentTimeMillis();

        //Leer y enviar archivo
        try {
            int cont = 1;
            br = new BufferedReader(new FileReader(new File(PATH)));

            System.out.println("Sending data from: "+PATH);
            while ((line = br.readLine()) != null) {
                //System.out.println(line);
                producer.send(new ProducerRecord<>(TOPIC, time + cont, line));
                cont++;
                if (cont % 50000 == 0) {
                    //System.out.println("Sleeping");
                    Thread.sleep(1000);
                }

            }
        } finally {
            if (br != null)
                br.close();
        }

        //Liberar productor
        producer.flush();
        producer.close();
    }
}
