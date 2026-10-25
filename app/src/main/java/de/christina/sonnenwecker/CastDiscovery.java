package de.christina.sonnenwecker;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Findet Chromecast-fähige Lautsprecher im WLAN. Blockierend – nicht im UI-Thread aufrufen. */
public final class CastDiscovery {
    private CastDiscovery() { }

    public static final class Device {
        public String name, host;
        public int port;
    }

    /**
     * @param stopAtName wenn gesetzt, wird abgebrochen, sobald ein Gerät mit diesem Namen gefunden ist
     */
    public static List<Device> discover(Context ctx, long timeoutMs, String stopAtName) {
        NsdManager nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
        WifiManager wifi = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = wifi.createMulticastLock("sonnenwecker-mdns");
        lock.setReferenceCounted(false);
        lock.acquire();

        List<Device> found = new ArrayList<>();
        LinkedBlockingQueue<NsdServiceInfo> toResolve = new LinkedBlockingQueue<>();
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String type, int err) { }
            @Override public void onStopDiscoveryFailed(String type, int err) { }
            @Override public void onDiscoveryStarted(String type) { }
            @Override public void onDiscoveryStopped(String type) { }
            @Override public void onServiceFound(NsdServiceInfo info) { toResolve.offer(info); }
            @Override public void onServiceLost(NsdServiceInfo info) { }
        };
        try {
            nsd.discoverServices("_googlecast._tcp", NsdManager.PROTOCOL_DNS_SD, listener);
            long end = System.currentTimeMillis() + timeoutMs;
            while (true) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                NsdServiceInfo info = toResolve.poll(left, TimeUnit.MILLISECONDS);
                if (info == null) break;
                Device d = resolve(nsd, info);
                if (d == null) continue;
                boolean dup = false;
                for (Device e : found) if (e.host.equals(d.host) && e.port == d.port) dup = true;
                if (!dup) found.add(d);
                if (stopAtName != null && stopAtName.equals(d.name)) break;
            }
        } catch (InterruptedException ignored) {
        } finally {
            try { nsd.stopServiceDiscovery(listener); } catch (Exception ignored) { }
            lock.release();
        }
        return found;
    }

    @SuppressWarnings("deprecation")
    private static Device resolve(NsdManager nsd, NsdServiceInfo info) throws InterruptedException {
        final Device[] result = new Device[1];
        CountDownLatch done = new CountDownLatch(1);
        nsd.resolveService(info, new NsdManager.ResolveListener() {
            @Override public void onResolveFailed(NsdServiceInfo s, int err) { done.countDown(); }
            @Override public void onServiceResolved(NsdServiceInfo s) {
                InetAddress host = s.getHost();
                if (host != null) {
                    Device d = new Device();
                    d.host = host.getHostAddress();
                    d.port = s.getPort();
                    d.name = s.getServiceName();
                    Map<String, byte[]> attrs = s.getAttributes();
                    if (attrs != null && attrs.get("fn") != null) {
                        d.name = new String(attrs.get("fn"), StandardCharsets.UTF_8);
                    }
                    result[0] = d;
                }
                done.countDown();
            }
        });
        done.await(5, TimeUnit.SECONDS);
        return result[0];
    }
}
