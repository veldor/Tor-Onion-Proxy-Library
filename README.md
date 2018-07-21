# This repo fixes the a minor DNS resolution issue for onion addresses that I faced when using the sample code provided in the README of [jehy/Tor-Onion-Proxy-Library](https://github.com/jehy/Tor-Onion-Proxy-Library). Check out the sample app included in this repo for reference on how to use this library. Also read the instructions below written by [jehy](https://github.com/jehy) in his fork.


Tor Onion Proxy Library
=======================
[![](https://jitpack.io/v/jehy/Tor-Onion-Proxy-Library.svg)](https://jitpack.io/#jehy/Tor-Onion-Proxy-Library)

# What is this fork?
This is a fork of [Thali Projects's Tor Onion Proxy Library](https://github.com/thaliproject/Tor_Onion_Proxy_Library) which was pretty outdated,
hard to build, contained no release of library itself and no simple examples. I updated it's components, made build easier and added release library.
Readme is updated to reflect those changes and contains a simple example on how to use this library.
Also I removed all data on non-android builds - there are many other easier ways to use Tor on Windows and OS/X.

# What is this project?
NOTE: This project exists independently of the Tor Project.

__What__: Enable Android and Java applications to easily host their own Tor Onion Proxies using the core Tor binaries. Just by including an AAR or JAR an app can launch and manage the Tor OP as well as start a hidden service.

__Why__: It's sort of a pain to deploy and manage the Tor OP, we want to make it much easier.

__How__: We are really just a thin Java wrapper around the Tor OP binaries and jtorctl. 

# How do I include this library?
There are three ways to use this library:
* Build it using instructions below;
* Download compiled version from releases section;
* Add it as maven dependency

Please note that (slf4j libraries are required for logging):
```groovy

allprojects {
    repositories {
    maven { url 'https://jitpack.io' }
    }
}

dependencies {
    compile 'com.github.jehy:Tor-Onion-Proxy-Library:0.0.7'
    compile 'org.slf4j:slf4j-api:1.7.7'
    compile 'org.slf4j:slf4j-android:1.7.7'
}

```

# Currently used Tor version

This fork is built using
* latest tor for jan 5, 2018 (0.3.1.9)


# How do I use this library?
First, you need to run Tor service. That's pretty simple:
```Java
String fileStorageLocation = "torfiles";
OnionProxyManager onionProxyManager =
            new AndroidOnionProxyManager(getApplicationContext(), fileStorageLocation);
int totalSecondsPerTorStartup = 4 * 60;
int totalTriesPerTorStartup = 5;
try {
  boolean ok = onionProxyManager.startWithRepeat(totalSecondsPerTorStartup, totalTriesPerTorStartup);
  if (!ok)
    Log.e("TorTest", "Couldn't start Tor!");
  }
  catch (InterruptedException | IOException e) {
    e.printStackTrace();
}
```
After it, you should wait for full Tor initialization:
```Java
while (!onionProxyManager.isRunning())
  Thread.sleep(90);
```
If everything is okay, you should have Tor listening to some random port on your localhost:
```Java
Log.v("My App", "Tor initialized on port " + onionProxyManager.getIPv4LocalHostSocksPort());
```

But the fun just begins. Your Http software needs to use Tor proxy. I don't know what libraries can use socks4a protocol out of box and I like apache's HttpComponents.
Unfortunately, httpComponents don't support Socks4a out of box because it tries to resolve DNS it self which is unacceptable for Tor.
So I created custom ConnectionSocketFactory and SSLConnectionSocketFactory which make handshake and create socket themselves:

**SSLConnectionSocketFactory:**
```Java
public class MySSLConnectionSocketFactory extends SSLConnectionSocketFactory {

    public MySSLConnectionSocketFactory(final SSLContext sslContext) {
        super(sslContext);
    }

    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        InetSocketAddress socksaddr = (InetSocketAddress) context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
        return new Socket(proxy);
    }

    @Override
    public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress,
                                InetSocketAddress localAddress, HttpContext context) throws IOException {
        // Convert address to unresolved
        InetSocketAddress unresolvedRemote = InetSocketAddress
                .createUnresolved(host.getHostName(), remoteAddress.getPort());
        return super.connectSocket(connectTimeout, socket, host, unresolvedRemote, localAddress, context);
    }
}
```

**ConnectionSocketFactory:**
```Java
public class MyConnectionSocketFactory extends PlainConnectionSocketFactory {

    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        InetSocketAddress socksaddr = (InetSocketAddress) context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
        return new Socket(proxy);
    }

    @Override
    public Socket connectSocket(
            int connectTimeout,
            Socket socket,
            final HttpHost host,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress,
            final HttpContext context) throws IOException, ConnectTimeoutException {

        InetSocketAddress unresolvedRemote = InetSocketAddress
                .createUnresolved(host.getHostName(), remoteAddress.getPort());
        return super.connectSocket(connectTimeout, socket, host, unresolvedRemote, localAddress, context);
    }
}
```
It is very easy to use. At first, create HttpClient which uses those factories:
```Java
//HttpClient tries to resolve host names locally, so by creating this FakeDnsResolver, it can be avoided
// Source of this hack: https://stackoverflow.com/a/25203021
static class FakeDnsResolver implements DnsResolver {
	@Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
    	// Return some fake DNS record for every request, we won't be using it
        return new InetAddress[] { InetAddress.getByAddress(new byte[] { 1, 1, 1, 1 }) };
    }
}


public HttpClient getNewHttpClient() {

	Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new MyConnectionSocketFactory())
                .register("https", new MySSLConnectionSocketFactory(SSLContexts.createSystemDefault()))
                .build();
	PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg,new FakeDnsResolver());
    return HttpClients.custom()
                .setConnectionManager(cm)
                .build();
}
```
Then set your Tor proxy:
```Java
  HttpClient cli = getNewHttpClient();
  int port = onionProxyManager.getIPv4LocalHostSocksPort();
  InetSocketAddress socksaddr = new InetSocketAddress("127.0.0.1", port);
  HttpClientContext context = HttpClientContext.create();
  context.setAttribute("socks.address", socksaddr);
  //http://wikitjerrta4qgz4.onion/
  //https://api.duckduckgo.com/?q=whats+my+ip&format=json
  HttpGet httpGet = new HttpGet("http://wikitjerrta4qgz4.onion/");
  HttpResponse httpResponse = httpClient.execute(httpGet, context);
  HttpEntity httpEntity = httpResponse.getEntity();
  InputStream httpResponseStream = httpEntity.getContent();
  BufferedReader httpResponseReader = new BufferedReader(
                        new InputStreamReader(httpResponseStream, "iso-8859-1"), 8);
  String line = null;
  while ((line = httpResponseReader.readLine()) != null) {
    System.out.println(line);
  }
  httpResponseStream.close();
```
That's all! You can use your HttpClient like on all other regular requests!
By the way, since google deprecated httpClient (which was really an ugly plan of Jesse Wilson to promote OkHttp), currently I use the httpClient from [here](https://github.com/smarek/httpclient-android).

# Acknowledgements
A huge thanks to Michael Rogers and the Briar project. This project started by literally copying their code (yes, I asked first) which handled things in Android and then expanding it to deal with Java. We are also using Briar's fork of JTorCtl until their patches are accepted by the Guardian Project.

Another huge thanks to the Guardian folks for both writing JTorCtl and doing the voodoo to get the Tor OP running on Android.

And of course an endless amount of gratitude to the heroes of the Tor project for making this all possible in the first place and for their binaries which we are using for all our supported Java platforms.

# FAQ


## How to build aar file on Linux

define the ANDROID_HOME environment variable, pointing to Android Sdk and start gradle:

```export ANDROID_HOME=/home/marco/Android/Sdk/
bash gradlew assembleRelease
```
the resulting file is:

./build/outputs/aar/ThaliOnionProxyAndroid-release.aar

You can use this aar directly in your Android Studio project, 
check example [here](https://stackoverflow.com/questions/24506648/adding-local-aar-files-to-gradle-build-using-flatdirs-is-not-working/28816265).

You can import a local aar file via the File>New>New Module>Import .JAR/.AAR Package option in Android Studio.

Then add the following to build.gradle:

```
dependencies {
    compile project(':ThaliOnionProxyAndroid-release')
    compile 'org.slf4j:slf4j-api:1.7.7'
    compile 'org.slf4j:slf4j-android:1.7.7'
}    
```

## What is the maturity of the code in this project?
Well the release version is currently 0.0.6 so that should say something. This is an alpha. We have (literally) one test. Obviously we need a heck of a lot more coverage. But we have run that test and it does actually work which means that the Tor OP is being run and is available.

## Can I run multiple programs next to each other that use this library?
Yes, they won't interfere with each other. We use dynamic ports for both the control and socks channel. 

## Can I help with the project?
ABSOLUTELY! Pull requests are welcome.

What we most need help with right now is test coverage. But we also have a bunch of features we would like to add. See our issues for a list.

## Where does jtorctl code come from?
This is code from [GuardianProject guys](https://github.com/guardianproject/jtorctl) with fixes from briar.

## Where did the binaries for the Tor OP come from?
The ARM binary for Android came from the [OrBot distribution](https://guardianproject.info/releases/). I take the latest PIE release qualify APK, unzip it and go to res/raw and then decompress tor.mp3 and go into bin and copy out the tor executable file and put it into android/src/main/assets

## Where did the geoip and geoip6 files come from?
I took them from the Data/Tor directory of the [Windows Expert Bundle](https://www.torproject.org/download/download.html.en).

## Why does the Android code require minSdkVersion 16?!?!?! Why so high?
The issue is the tor executable that I get from Guardian. To run on Lollipop the executable has to be PIE. But PIE support only started with SDK 16. So if I'm going to ship a PIE executable I have to set minSdkVersion to 16. But!!!!! Guardian actually also builds a non-PIE version of the executable. So if you have a use case that requires support for an SDK less than 16 PLEASE PLEASE PLEASE send mail to the [Thali Mailing list](https://pairlist10.pair.net/mailman/listinfo/thali-talk). We absolutely can fix this. We just haven't had a good reason to. So please give us one!

## Code of Conduct
This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
