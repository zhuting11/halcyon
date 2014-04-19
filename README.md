Halcyon
=======

Halcyon is a scalable, cross-platform, embeddable, open-source OPC-UA server.


Building and Running Halcyon
-------

### Step 0
Install JDK8 and make sure it's either a) the default JDK on your path or b) the JDK `$JAVA_HOME` points to.

### Step 1
Clone, build, and install [mezzanine](https://github.com/digitalpetri/mezzanine), the OPC-UA SDK Halcyon is built on.

```
git clone https://github.com/digitalpetri/mezzanine.git
cd mezzanine
mvn install
```
This step will only be necessary until mezzanine is being released to Maven Central.

### Step 2
Clone, build, and package [halcyon](https://github.com/digitalpetri/halcyon).

```
git clone https://github.com/digitalpetri/halcyon.git
cd halcyon
mvn package
```

If packaging halcyon was successful a zip distribution can be found under `halcyon-jsw/target` called `halcyon-0.1.0-SNAPSHOT.zip` (or whatever the current version actually is...)

### Step 3
Install and configure the halcyon distribution...

```
mkdir /opt/halcyon
unzip -d /opt/halcyon halcyon-jsw/target/halcyon-0.1.0-SNAPSHOT.zip
cd /opt/halcyon
chmod +x bin/halcyon.sh
```

At this point you'll probably want to uncomment and modify the `wrapper.java.additional.1=-Dhostname=localhost` parameter in `config/wrapper.conf` so it points to a resolvable hostname or the IP address.

### Step 4
Start Halcyon

```
/opt/halcyon/bin/halcyon.sh start
```

