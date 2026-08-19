package com.cherif.send.transfer;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

@CapacitorPlugin(name = "LocalTransfer")
public class LocalTransferPlugin extends Plugin {

    @PluginMethod
    public void getLocalAddress(PluginCall call) {
        try {
            String address = findLocalAddress();

            if (address == null) {
                call.reject("Aucune adresse réseau locale trouvée.");
                return;
            }

            JSObject result = new JSObject();
            result.put("address", address);
            call.resolve(result);

        } catch (Exception e) {
            call.reject("Impossible de récupérer l'adresse locale.", e);
        }
    }

    private String findLocalAddress() throws Exception {
        Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                continue;
            }

            Enumeration<InetAddress> addresses =
                    networkInterface.getInetAddresses();

            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();

                if (address instanceof Inet4Address
                        && !address.isLoopbackAddress()) {
                    return address.getHostAddress();
                }
            }
        }

        return null;
    }
}
