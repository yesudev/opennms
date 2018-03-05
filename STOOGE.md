# Project Stooge Operation Sentinel

The goal of the _Sentinel Operation_ is to refine the work which was done in the original [Stooge](https://github.com/OpenNMS/opennms/tree/jw/stooge-poc) project as well as the work for [Sentinel](https://github.com/OpenNMS/opennms/compare/features/sentinel?expand=1).
The main focus of _Operation Sentinel_ is to find a way of re-using the existing Spring and Blueprint defintions.

## Motivation

At the moment _{opennms-product-name}_ starts up a Jetty and Apache Karaf container.
In the "Jetty world" Spring is heavily used. In the "Osgi world" we make use of Apache Aries blueprints and Osgi specific features.
In order to have both worlds know of each other, a so called _OSGI Bridge_ exists, which allows to forward services in both directions, but mostly from the "Jetty world" into the "Osgi world" (`onmsgi:service`statements).
Besides that all classes loaded by Jetty are forwarded through the Apache Karaf System bundle to prevent Karaf to load the classes twice and allow them to be used.
Otherwise an Object of type A from the Karaf ClassLoader would not be assignable to an Object of type A from the Jetty ClassLoader.

When _{opennms-product-name}_ starts up the Jetty Container uses existing Spring Application Context Definitions (e.g. `META-INF/opennms/applicationContext-dao.xml`, `META-INF/opennms/component-dao.xml`, etc)
and the Apache Karaf container uses Blueprint Defintions (e.g. `OSGI_INF/blueprint/blueprint.xml`).

If we were able to re-use both defintion files, the amount of work re-writing the exactly same definitions is minimized. 
In addition the full potential of Spring can be used (e.g. `@Transactional`), which otherwise must be re-implemented in some other way.
 
## Review

This chapter takes a look at the existing proof of concepts and (re-)evaluates them.
Both POCs aim for a single Apache Karaf Container which can load existing OpenNMS jar's. 
Especially the initialization of the DAO-Context is vital.

### Stooge POC

The [Stooge POC](https://github.com/OpenNMS/opennms/tree/jw/stooge-poc) builds an uber bundle with all dependencies to bootstrap the `opennms-dao` module:

> Here we were able to get the DAOs and a JMS Sink Consumer working in a vanilla Karaf instance.
The DAOs are loaded in a mega fat shaded .jar and the implementations are exposed using the existing ONMSGi registry directives.

To achieve this it uses an osgi-aware spring application context from the [gemini blueprint](https://www.eclipse.org/gemini/blueprint/) project.

#### Pro

 * DAOs are exposed in a vanilla Karaf Container
 * Straight-forward approach
 * Re-usage of existing application contexts
 * No changes to existing `blueprint.xml` files


#### Cons

 * hard to maintain, as the `Import-Package` and `Export-Package` statements of the `MANIFEST.MF` file have to be maintained manually
 * Also includes spring and hibernate related classes, which prevent the usage of spring in any other module (e.g. `@Transactional` or `TransactionOperations`)
 * The same goes for hibernate
 * Basically any other bundle which starts in the container, cannot consume hibernate, spring or `onmsgi:service` statements
 * Does not enable spring application context extensions (refer to the `ChainActivator` in the `gemini-core` project)
 * Does not allow for dynamic detection of `applicationContext-*.xml` files in other modules (fixed/hard-coded to a list of application context xml files)

#### Summary

The POC achieves consuming DAOs in another bundle within a vanilla Apache Karaf container successfully.
However the caveats are that spring or hibernate related classes are not exported correctly and cannot be used.
In addition the `MANIFEST.MF`'s `Import-Package` and `Export-Package` headers have to be maintained manually.

In summary this is a good starting point, but the Cons must be overcome to have a reliable and easier to maintain solution.

### Project Sentinel Bootstrap

This POC tries to overcome the problems of the `Stooge POC` by simply starting the `gemini-extender` module instead of just using the osgi aware application context.

#### Pro

 * Hibernate and Spring classes are exported correctly
 * Full potential of spring is used (e.g. custom application extensions, such as `onmsgi:services` are supported)
 
#### Cons

 * Automatically loads blueprint.xml AND applicationContext.xml
 * Apache Aries blueprint extender must be stopped and is replaced with the Eclipse Gemini Blueprint.
 * Focuses too much on getting already existing modules working in an already working OpenNMS container 
 
#### Summary

Using Eclipse Gemini Blueprint Extender over the Apache Aries Blueprint Extender should in theory work.
However, providing a custom `HttpService` within Apache Karaf should also work, but we have some (not-critical) side-effects
with that already. 

It is to be expected to see some side-effects when exchanging the blueprint extenders (e.g. `cm:property-placeholder` did not work out of the box).
In addition it may require changes to already existing and working modules.

## Yet Another Approach

Here we try to combine the work from both POCs.
The uber jar approach is used to build a DAO uber module (containing all DAO-API and DAO modules) as well as using the gemini blueprint osgi aware application context.
However instead of replacing the Apache Aries Blueprint Extender with the Gemini Blueprint Extender, it exposes a [BundleActivator](features/stooge/bootstrap/src/main/java/org/opennms/netmgt/stooge/bootstrap/Activator.java) and only listens for spring application context files.
It completely ignores blueprint defintions, thus allowing Apache Aries Blueprint Extender of taking care.
In addition a `Spring-Context` header in the `MANIFEST.MF` file points to a list of application contexts to load.

### Pro

 * Spring and Hibernate classes are exported correctly and allow the usage of those classes in other modules
 * re-use of existing spring application contexts and thus the full potential of spring 
 * re-use of existing technologies with hopefully minimal changes to existing blueprint/applicationContext files

### Cons

 * Limited classpath scan capabilities (e.g. required for creation of `SessionFactory`) and thus may be re-implemented manually
 * Uber DAO jar
 * Exposes dummy services, which will not work in a stand-alone container as they require an OpenNMS instance (e.g. `onms-server.xml`)

### Summary

This approach combines the two worlds of spring and osgi.
By simply using the blueprint extender and not directly starting it, we prevent unnecessary conflicts.
In addition we are able to implement changes if we require them and are more flexible.

## Setup

### OpenNMS + Minion

First, get Minion + OpenNMS running.

In Minion shell, run the following:
```
config:edit org.opennms.features.telemetry.listeners-udp-2222
config:property-set name TEST
config:property-set class-name org.opennms.netmgt.telemetry.listeners.udp.UdpListener
config:property-set listener.port 2222
config:update
```

### The Stooge

In a vanilla Karaf 4.1.2 distribution.

Edit `featuresRepositories` in `etc/org.apache.karaf.features.cfg` to include:
```
mvn:org.apache.karaf.features/spring/4.0.10/xml/features, \
mvn:org.opennms.karaf/opennms/22.0.0-SNAPSHOT/xml/features
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
feature:install stooge-bootstrap stooge-model stooge-dao  
feature:install stooge-telemetry
feature:install stooge-test
```

### Another stooge instance

Same as above, just modify the ports so that they don't conflict.

## Generating traffic

Generate test traffic using:
```
./udpgen -h 127.0.0.1 -p 2222
```

## Next Steps/Problems

### Session Factory Problem
Even in this POC an uber jar was not avoidable.
Especially the DAOs must probably long term still be re-packaged into a single dao module.
This is probably fine, as in order to create the `SessionFactory` bean.

### OpenNMS Data Model Problem
However the DAOs rely on the _OpenNMS Data Model_ (not the `opennms-model` module), which consists of a various set of `core-modules`, `opennms-config-*` and the `opennms-model` modules.
Without these it is impossible to get the DAOs working.

Therefore it is probably crucial to re-work the structure of those modules and make more compliant with the "OSGI WAY".

### OpenNMS Dependency Mixture
In the long run it would be nice to scale out each daemon individually.
Therefore it would be nice to only load modules the daemon requires. 
With the current dependency set (See above) this is not possible (e.g. some `snmp-api` modules are required).
The dependencies of the "old" modules should be revisited and made mor "OSGI"-ish.

### Existing Feature Defintions
The existing feature definitions work when an existing OpenNMS instance is running.
However they will not work in a standalone container.
For example the `opennms-flows` feature also exposes ReST services, which is fine when running in the {opennms-product-name} container,
but should not be done when running in a vanilla Karaf Container (Sentinel) instance. 

