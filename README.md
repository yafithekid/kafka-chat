# Kafka Chat
simple IRC chat with Apache Kafka

### Members

- 13512014 Muhammad Yafi
- 13512066 Calvin Sadewa

### Concepts

Each nickname will create a consumer group. If the user types `/NICK` commmand, the client will fetch user channel list from
kafka broker. The result will be buffered in list of string in client memory, say `channelList`.
```
Assume calvin joins pat and rplsd
> /NICK calvin
Server                                         Client
##############################      #########################################################
# Topic:channel_list_calvin  #      #   connect to topic channel_list_calvin                #
# messages:                  #      #   connector.createMessageStreams                      #
# pat                        #      #                                                       #
# rplsd                      #      #   ...                                                 #
#                            #      #   channelList -> list of string contains [pat][rplsd] #
##############################      #########################################################
```

When a user types `/JOIN` or `/LEAVE`, it will push or pop the related channel from the `channelList`.
```
>/JOIN tbd
Client
#############################
# channelList.add("tbd")    # -> channelList will contains pat,rplsd,tbd
#############################

> /LEAVE rplsd
Client
##################################
# channelList.remove("rplsd")    # -> channelList will contains pat,tbd
##################################
```
When a user sends a message, it will create a kafka producer which will send the message to the associated
channel. One channel represented as one kafka topic. If the kafka topic doesn't exist, the kafka will create new topic.
When a user send a broadcast message, client will send message to each of the channel based on the `channelList` data.
```
@pat hello
Client                                                  Server
###############################################     ###########################
# producer = create kafka producer            #     # Topic pat:              #
# record = new ProducerRecord("pat","hello")  # ->  # message:                #
# producer.send(record)                       #     # hello                   #
###############################################     ###########################
```
After each command, the program will pull messages. It will create kafka stream, `createMessageStreams` with first parameter is `Map<String,Integer>`.
The first parameter will be used by kafka to get the message streams from the topics we interested list in (aka. kafka topic)
```
Client
###########################################################################################
# topicCountMap : Map<String,Integer> contains list of channel name, based on channelList #
# consumerMap = createMessageStream(topicCountMap)                                        #
# ...(fetch message from consumerMap)...                                                  #
###########################################################################################
```
When the user types `/EXIT`, the client will send the content of `channelList` to user channel list topic
```
Server
##############################
#Topic:channel_list_calvin   #
#messages:                   #
#pat                         #
#rplsd                       #
#<delimiter>                 #
#pat                         #
#tbd                         #
##############################
```
We use `<delimiter>` since we cannot delete message in a topic. So, when the user log in again,
it will get messages `[pat][rplsd][<delimiter>][pat][tbd]`. The user will parse the message and
takes the channel names to `channelList` based on the last `<delimiter>`. Kafka will delete some messages in the topic based on when each message come to the queue,
so the old channel list (which we not used) will be deleted automatically. Note when the user doesn't log in for a long period,
the most recent channel list user has will be removed too! We think that this is the drawback of using kafka as a IRC.
### Available commands

1. `/NICK <nickname>`: change your nickname to **nickname**. Default nickname is `paijo`
2. `/JOIN <channelname>`: join current nickname to **channelname**
3. `/LEAVE <channel>`: leave current nickname from **channel**
4. `/EXIT`: terminate the application
5. `@<channelname> <any_text>` send **any_text** to **channelname**
6. `<any_text>` send **any_text** to all channels joined by nickname.

### Prerequisites

1. Install Java JDK 1.8
2. Install Gradle, add it to your PATH environment variables.
3. Ensure gradle can works by typing `gradle` in your command prompt.

### Run the jar files

1. Run `gradle clientJar` on root project dir
2. In `build/libs` folder, run `java -jar kafka-chat-client-1.0-SNAPSHOT.jar <IP_zookeper:PORT> <IP_bootstrap_server:PORT>` example
```
    java -jar kafka-chat-client-1.0-SNAPSHOT.jar 167.205.34.208:2181 167.205.34.208:9092
```
