# Cluster Sharding sample

The KillrWeather sample illustrates how to use [Apache Pekko Cluster Sharding](https://pekko.apache.org/docs/pekko/current/java/typed/cluster-sharding.html) in Java, for the same sample in Scala see [Cluster Sharding Sample Scala](https://github.com/apache/incubator-pekko-samples/tree/main/pekko-sample-sharding-scala)
It also shows the basic usage of [Apache Pekko HTTP](https://pekko.apache.org/docs/pekko-http/current/index.html).

The sample consists of two applications, each a separate maven submodule:
 
 * *killrweather* - A distributed Apache Pekko cluster that shards weather stations, each keeping a set of recorded 
   data points and allowing for local querying of the records. The cluster app has a HTTP endpoint for recording
   and querying the data per weather station. 
 
 * *killrweather-fog* - A client that periodically submits random weather measurements for a set of stations to a 
   running cluster.
 

Let's start going through the implementation of the cluster!
 
## KillrWeather

Open [KillrWeather.java](killrweather/src/main/java/sample/killrweather/KillrWeather.java).
This program starts an `ActorSystem` which joins the cluster through configuration, and starts a `Guardian` actor for the system. 

### Guardian

The [Guardian.java](killrweather/src/main/java/sample/killrweather/Guardian.java) bootstraps the application to shard 
`WeatherStation` actors across the cluster nodes.

Setting up sharding with the entity is done in [WeatherStation.java](killrweather/src/main/java/sample/killrweather/WeatherStation.java#L38).
Keeping the setup logic together with the sharded actor and then calling it from the bootstrap logic of the application 
is a common pattern to structure sharded entities.

### WeatherStation - sharded data by id
 
Each sharded `WeatherStation` actor has a set of recorded data points for a station identifier. 

It receives that data stream from remote devices via the
HTTP endpoint. Each `WeatherStation` and also respond to queries about it's recorded set of data such as:

* current
* averages 
* high/low 

### Receiving edge device data by data type

The [WeatherHttpServer](killrweather/src/main/java/sample/killrweather/WeatherHttpServer.java) is started with 
 [WeatherRoutes](killrweather/src/main/java/sample/killrweather/WeatherRoutes.java)
to receive and unmarshall data from remote devices by station ID to allow 
querying. To interact with the sharded entities it uses the [`EntityRef` API](killrweather/src/main/java/sample/killrweather/WeatherRoutes.java#L40).

The HTTP port of each node is chosen from the port used for Apache Pekko Remoting plus 10000, so for a node running 
on port 7355 the HTTP port will be 17355.

### Configuration

This application is configured in [killrweather/src/main/resources/application.conf](killrweather/src/main/resources/application.conf)
Before running, first make sure the correct settings are set for your system, as described in the pekko-sample-cluster tutorial.

## Fog Network

Open [Fog.java](killrweather-fog/src/main/java/sample/killrweather/fog/Fog.java).

`Fog` is the program simulating many weather stations and their devices which read and report data to clusters.
The name refers to [Fog computing](https://en.wikipedia.org/wiki/Fog_computing) with edges - the remote weather station
nodes and their device edges.

This example starts simply with one actor per station and just reports one data type, temperature. In the wild, other devices would include:
pressure, precipitation, wind speed, wind direction, sky condition and dewpoint.
`Fog` starts the number of weather stations configured in [killrweather-fog/src/main/resources/application.conf](killrweather-fog/src/main/resources/application.conf) 
upon boot.

### Weather stations and devices

Each [WeatherStation](killrweather-fog/src/main/java/sample/killrweather/fog/WeatherStation.java) is run on a task to trigger scheduled data sampling.
These samples are timestamped and sent to the cluster over HTTP using [Apache Pekko HTTP](https://pekko.apache.org/docs/pekko-http/current/index.html). 

## Apache Pekko HTTP example

Within KillrWeather are two simple sides to an HTTP equation.

**Client**

* [WeatherStation](killrweather-fog/src/main/java/sample/killrweather/fog/WeatherStation.java) - HTTP data marshall and send

**Server**

* [WeatherHttpServer](killrweather/src/main/java/sample/killrweather/WeatherHttpServer.java) - HTTP server
* [WeatherRoutes](killrweather/src/main/java/sample/killrweather/WeatherRoutes.java) - HTTP routes receiver which will unmarshall and pass on the data

Both parts of the application uses Jackson for marshalling and unmarshalling objects to and from JSON.

## Running the samples

### The KillrWeather Cluster

There are two ways to run the cluster, the first is a convenience quick start.

#### A simple three node cluster in the same JVM

The simplest way to run this sample is to run this in a terminal, if not already started:
   
    mvn compile
    mvn -pl killrweather exec:java
   
This command starts three (the default) `KillrWeather` actor systems (a three node cluster) in the same JVM process. 

#### Dynamic WeatherServer ports

In the log snippet below, note the dynamic weather ports opened by each KillrWeather node's `WeatherServer` for weather stations to connect to. 
The number of ports are by default three, for the minimum three node cluster. You can start more cluster nodes, so these are dynamic to avoid bind errors. 

```
[2020-01-16 13:44:58,842] [INFO] [] [pekko.actor.typed.ActorSystem] [KillrWeather-pekko.actor.default-dispatcher-3] [] - WeatherServer online at http://127.0.0.1:17345/
[2020-01-16 13:44:58,842] [INFO] [] [pekko.actor.typed.ActorSystem] [KillrWeather-pekko.actor.default-dispatcher-19] [] - WeatherServer online at http://127.0.0.1:53937/
[2020-01-16 13:44:58,843] [INFO] [] [pekko.actor.typed.ActorSystem] [KillrWeather-pekko.actor.default-dispatcher-15] [] - WeatherServer online at http://127.0.0.1:17355/
```

#### A three node cluster in separate JVMs

It is more interesting to run them in separate processes. Stop the application and then open three terminal windows.
In the first terminal window, start the first seed node with the following command:

    mvn -pl killrweather exec:java -Dexec.args="7345"

7345 corresponds to the port of the first seed-nodes element in the configuration. In the log output you see that the cluster node has been started and changed status to 'Up'.

You'll see a log message when a `WeatherStation` sends a message to record the current temperature, and for each of those you'll see a log message from the `WeatherRoutes` showing the action taken and the new average temperature.

In the second terminal window, start the second seed node with the following command:

    mvn -pl killrweather exec:java -Dexec.args="7355"

7355 corresponds to the port of the second seed-nodes element in the configuration. In the log output you see that the cluster node has been started and joins the other seed node and becomes a member of the cluster. Its status changed to 'Up'. Switch over to the first terminal window and see in the log output that the member joined.

Some of the temperature aggregators that were originally on the `ActorSystem` on port 7345 will be migrated to the newly joined `ActorSystem` on port 7355. The migration is straightforward: the old actor is stopped and a fresh actor is started on the newly created `ActorSystem`. Notice this means the average is reset: if you want your state to be persisted you'll need to take care of this yourself. For this reason Cluster Sharding and Apache Pekko Persistence are such a popular combination.

Start another node in the third terminal window with the following command:

    mvn -pl killrweather exec:java -Dexec.args="0"

Now you don't need to specify the port number, 0 means that it will use a random available port. It joins one of the configured seed nodes.
Look at the log output in the different terminal windows.

Start even more nodes in the same way, if you like.

#### Dynamic WeatherServer port

Each node's log will show its dynamic weather port opened for weather stations to connect to. 
```
[2020-01-16 13:44:58,842] [INFO] [] [pekko.actor.typed.ActorSystem] [KillrWeather-pekko.actor.default-dispatcher-3] [] - WeatherServer online at http://127.0.0.1:17345/
```


### Interacting with the HTTP endpoint manually

With the cluster running you can interact with the HTTP endpoint using raw HTTP, for example with `curl`.

Record data for station 62:

```
curl -XPOST http://localhost:17345/weather/62 -H "Content-Type: application/json" --data '{"eventTime": 1579106781, "dataType": "temperature", "value": 10.3}'
```

Query average temperature for station 62:

```
curl "http://localhost:17345/weather/62?type=temperature&function=average"
```

### The Fog Network
 
In a new terminal start the `Fog`, (see [Fog computing](https://en.wikipedia.org/wiki/Fog_computing))

Each simulated remote weather station will attempt to connect to one of the round-robin assigned ports for Fog networking over HTTP.

The fog application, when run without parameters, will expect the cluster to have been started without parameters as well so that the HTTP ports if has bound are predictable.
If you have started cluster nodes manually providing port numbers you will have to do the same with the fog app or else it will not be able to find the endpoints.   

For example:
 
    mvn -pl killrweather exec:java -Dexec.args="7345"
    sbt "killrweather-fog/runMain sample.killrweather.fog.Fog 8081 8033 8056"
     
### Shutting down

Shut down one of the nodes by pressing 'ctrl-c' in one of the terminal windows. The other nodes will detect the failure after a while, which you can see in the log output in the other terminals.
