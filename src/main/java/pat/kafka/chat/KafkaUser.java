package pat.kafka.chat;

import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.util.*;

import kafka.consumer.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Created by calvin-pc on 10/8/2015.
 * One user, one customer group
 * One user, one topic to record the channel they suscribe to
 * One channel, one topic
 */
public class KafkaUser {
    private kafka.javaapi.consumer.ConsumerConnector connector;
    private org.apache.kafka.clients.producer.KafkaProducer producer;
    private String zookeeper;
    private String nickname;
    private String bootstrapBroker;
    private List<String> channelList = new ArrayList<String>();

    public KafkaUser(String zookeeper,String bootstrapBroker) {
        this.zookeeper = zookeeper;
        this.bootstrapBroker = bootstrapBroker;
    }

    public void login(String nickname) {
        if (this.nickname != null) {
            exit();
        }
        this.nickname = nickname;
        ConsumerConfig config = createConsumerConfig(this.zookeeper,getCustomerGroup());
        connector = Consumer.createJavaConsumerConnector(config);

	    Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(getUserChannelsTopic(), 1);

	    Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = connector.createMessageStreams(topicCountMap);
        List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(getUserChannelsTopic());

        for (KafkaStream<byte[],byte[]> stream : streams) {
            ConsumerIterator<byte[], byte[]> it = stream.iterator();
            try {
                while (it.hasNext()) {
                    channelList.add(new String(it.next().message()));
                }
            }
            catch (Exception e) {
                //TIMEOUT_EXCEPTION
                //Excpected and a must in the code because no other
                //way to make KafkaStream forcefully poll
                //Expected to be changed at kafka 0.9
            }
        }

        producer = getProducer();
    }

    public void join(String channel) {
        if (channelList.contains(channel)) {
            channelList.add(channel);
        }
    }

    public void leave(String channel) {
        if (channelList.contains(channel)) {
            channelList.remove(channel);
        }
    }

    public void send(String channelName, String message) {
        String modifiedMessage = "[" + nickname + "][" + channelName + "]" + message;
        ProducerRecord<byte[],byte[]> record =
                new ProducerRecord<byte[],byte[]>(getChannelTopicName(channelName),
                        modifiedMessage.getBytes());
        producer.send(record);
    }

    public void sendAll (String message) {
        String modifiedMessage = "[broadcast][" + nickname + "]" + message;
        for (String channel : channelList) {
            ProducerRecord<byte[],byte[]> record =
                    new ProducerRecord<byte[],byte[]>(getChannelTopicName(channel),
                            modifiedMessage.getBytes());
            producer.send(record);
        }
    }

    public List<String> recieveMessages() {
	Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        for (String channel : channelList) {
            topicCountMap.put(getChannelTopicName(channel), 1);
        }

	Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = connector.createMessageStreams(topicCountMap);

	List<String> messages = new ArrayList<String>();	
	for (String channel : channelList) {
    		List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(getChannelTopicName(channel));
       
            for (KafkaStream<byte[],byte[]> stream : streams) {
                ConsumerIterator<byte[], byte[]> it = stream.iterator();
                try {
                    while (it.hasNext())
                        messages.add(new String(it.next().message()));
                }
                catch (Exception e) {
                    //TIMEOUT_EXCEPTION
                    //Excpected and a must in the code because no other
                    //way to make KafkaStream forcefully poll
                    //Expected to be changed at kafka 0.9
                }
            }
        }
 
        return messages;
    }

    public void exit() {
        for (String channel : channelList) {
            ProducerRecord<byte[],byte[]> record = new ProducerRecord<byte[],byte[]>(getUserChannelsTopic(),channel.getBytes());
            producer.send(record);
        }
	if (this.connector != null) {
        	this.connector.shutdown();
	}
        if (this.producer != null) {
		this.producer.close();
	}
        this.nickname = null;
        this.channelList = new ArrayList<String>();
    }

    private KafkaProducer getProducer() {
        java.util.Map<java.lang.String,java.lang.Object> configs = new HashMap<String, Object>();
	    configs.put("bootstrap.servers",bootstrapBroker);
        configs.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        return new KafkaProducer(configs);
    }

    private String getChannelTopicName (String channel) {
        return "channel_is_" + channel;
    }

    /**
     * return the user channel topic of user
     * @return the user channel topic name of user
     */
    private String getUserChannelsTopic () {
        return "channel_list_" + nickname;
    }

    /**
     * return the customer group name of user
     * @return the customer group name of user
     */
    private String getCustomerGroup () {
        return "customer_group_" + nickname;
    }

    private static ConsumerConfig createConsumerConfig(String a_zookeeper, String a_groupId) {
        Properties props = new Properties();
        props.put("zookeeper.connect", a_zookeeper);
        props.put("group.id", a_groupId);
        props.put("zookeeper.session.timeout.ms", "400");
        props.put("zookeeper.sync.time.ms", "200");
        props.put("auto.commit.interval.ms", "1000");
        props.put("consumer.timeout.ms","500");
        return new ConsumerConfig(props);
    }

    public static void main(String[] args) throws Exception {
        KafkaUser client = new KafkaUser(args[0], args[1]);
        try {
            boolean stop = false;
            do{
                System.out.println("prompt>");
                Scanner sc = new Scanner(System.in);
                String str = sc.nextLine();
                String[] splited = str.split("\\s+");
                if (splited[0].equals("/NICK")){
                    if (splited.length != 2){
                        System.out.println("Usage: /NICK <nickname>");
                    } else {
                        client.login(splited[1]);
                        System.out.println("[OK] Nickname changed to '"+splited[1]+"'");
                    }

                } else if (splited[0].equals("/JOIN")){
                    if (splited.length != 2){
                        System.out.println("Usage: /JOIN <nickname>");
                    } else {
                        client.join(splited[1]);
                    }

                } else if (splited[0].equals("/LEAVE")){
                    if (splited.length != 2){
                        System.out.println("Usage: /LEAVE <channel>");
                    } else {
                        client.leave(splited[1]);
                    }
                } else if (splited[0].equals("/EXIT")){
                    stop = true;
                    client.exit();
                } else {
                    StringBuffer message = new StringBuffer();
                    if (splited[0].startsWith("@")){
                        if (splited.length < 2){
                            System.out.println("Usage: @<channel> <text>");
                        } else {
                            String channelName = splited[0].substring(1);
                            for(int i = 1; i < splited.length; i++){
                                if (i > 1) message.append(" ");
                                message.append(splited[i]);
                            }
                            client.send(channelName,message.toString());
                        }
                    } else {
                        for(int i = 0; i < splited.length; i++){
                            if (i > 0) message.append(" ");
                            message.append(splited[i]);

                        }
                        client.sendAll(message.toString());
                    }
                }
                if (!stop){
                    List<String> messages = client.recieveMessages();
                    for(String message: messages){
                        System.out.println(message);
                    }
                }
            } while (!stop);
            System.out.println("bye");
        }
        finally {
            client.exit();
        }
    }
}
