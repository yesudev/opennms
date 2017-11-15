# Project Stooge

The goal of this project is to find a runtime that allows us to scale out different processes along side OpenNMS in order to help alleviate the single OpenNMS JVM as a bottleneck.
Short term we're focusing specifically on processing flows in parallel, however the runtime and general framework should be reusable for other processes such as syslog and traps.

Most of the processes require some form of database access, i.e. accessing the NodeDao, so it's important that the DAOs be made available in this runtime, preferably with minimal code changes.

## Research

### Using another OpenNMS Instance

#### Pro

* No new runtime to build

#### Con

* Heavy. Uses alot more resources than what we actually need.
* Hard to fine tune which components are loaded: 
   * We need to start the whole webapp to be able to any single feature in Karaf
   * The complete DAO context is always loaded

### Spring Boot

#### Pro

* Developer friendly
* Tons of resources and features

#### Cons

* Could not reuse the entity models since we use an older version of Hibernate.
   * Specifically, the interface has changed on some of the type adapters, so we can't load the models as is.
* Classpath conflicts with some of the runtime components, which make it difficut to reuse the existing code.

### Karaf

#### Pro

* Leverage existing feature definitions and bundles
* Similar to Minion
* One step closer to running OpenNMS on Karaf (instead of Karaf on OpenNMS)

#### Con

* Another Karaf instance
 
## Proof of concept

### Overview

Here we were able to get the DAOs and a JMS Sink Consumer working in a vanilla Karaf instance.

The DAOs are loaded in a mega fat shaded .jar and the implementations are exposed using the existing ONMSGi registry directives.

### Setup

#### OpenNMS + Minion

First, get Minion + OpenNMS running.

In Minion shell, run the following:
```
config:edit org.opennms.features.telemetry.listeners-udp-2222
config:property-set name TEST
config:property-set class-name org.opennms.netmgt.telemetry.listeners.udp.UdpListener
config:property-set listener.port 2222
config:update
```

#### The Stooge

In a vanilla Karaf 4.1.2 distribution.

Edit `featuresRepositories` in `etc/org.apache.karaf.features.cfg` to include:
```
mvn:org.apache.karaf.features/spring/4.0.10/xml/features, \
mvn:org.opennms.karaf/opennms/22.0.0-SNAPSHOT/xml/features, \
mvn:org.opennms.karaf/opennms/22.0.0-SNAPSHOT/xml/features-minion
```

In `etc/org.apache.karaf.management.cfg` set:
* rmiRegistryPort = 2099
* rmiServerPort = 44445

In `etc/org.apache.karaf.shell.cfg` set:
* sshPort=8301

Start Karaf with:
```
JAVA_DEBUG_PORT=5006 ./bin/karaf
```

Run:
```
feature:install stooge-core stooge-telemetry
feature:install stooge-test
```

#### Another stooge instance

Same as above, just modify the ports so that they don't conflict.

### Generating traffic

Generate test traffic using:
```
./udpgen -h 127.0.0.1 -p 2222
```

