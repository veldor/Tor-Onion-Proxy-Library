package com.example.nandan.sampletorproxyapp;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.config.Registry;
import cz.msebera.android.httpclient.config.RegistryBuilder;
import cz.msebera.android.httpclient.conn.DnsResolver;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.impl.client.HttpClients;
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager;
import cz.msebera.android.httpclient.ssl.SSLContexts;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new TorTask().execute();

    }


    static class FakeDnsResolver implements DnsResolver {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
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



    private class TorTask extends android.os.AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            String fileStorageLocation = "torfiles";
            com.msopentech.thali.toronionproxy.OnionProxyManager onionProxyManager =
            new com.msopentech.thali.android.toronionproxy.AndroidOnionProxyManager(getApplicationContext(), fileStorageLocation);
            int totalSecondsPerTorStartup = 4 * 60;
            int totalTriesPerTorStartup = 5;
            try {
                boolean ok = onionProxyManager.startWithRepeat(totalSecondsPerTorStartup, totalTriesPerTorStartup);
                if (!ok)
                    System.out.println("Couldn't start tor");

                while (!onionProxyManager.isRunning())
                    Thread.sleep(90);
                System.out.println("Tor initialized on port " + onionProxyManager.getIPv4LocalHostSocksPort());

                HttpClient httpClient = getNewHttpClient();
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
            }
            catch (Exception e) {
                e.printStackTrace();

            }
            return "done!";
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected void onPostExecute(String result) {

        }
    }

}
