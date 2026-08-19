package com.cherif.send.transfer;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

@CapacitorPlugin(name = "LocalTransfer")
public class LocalTransferPlugin extends Plugin {

    private ServerSocket serverSocket;
    private Thread serverThread;
@PluginMethod
public void sendFile(PluginCall call) {
    String host = call.getString("host");
    int port = call.getInt("port", -1);
    String filePath = call.getString("filePath");
    String fileName = call.getString("fileName");

    if (host == null || port <= 0 || filePath == null) {
        call.reject("Paramètres d'envoi invalides.");
        return;
    }

    new Thread(() -> {
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                call.reject("Fichier introuvable.");
                return;
            }

            Socket socket = new Socket(host, port);

            java.io.OutputStream output = socket.getOutputStream();

            String header =
                    "CHERIF-SEND\n" +
                    fileName + "\n" +
                    file.length() + "\n";

            output.write(header.getBytes("UTF-8"));
            output.flush();

            java.io.FileInputStream input =
                    new java.io.FileInputStream(file);

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            output.flush();
            input.close();
            socket.close();

            call.resolve();

        } catch (Exception e) {
            call.reject("Échec de l'envoi local.", e);
        }
    }).start();
}
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

    @PluginMethod
    public void startReceiver(PluginCall call) {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                JSObject result = new JSObject();
                result.put("port", serverSocket.getLocalPort());
                call.resolve(result);
                return;
            }

            serverSocket = new ServerSocket(0);

            serverThread = new Thread(() -> {
                while (serverSocket != null && !serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        handleClient(socket);
                    } catch (IOException e) {
                        break;
                    }
                }
            });

            serverThread.start();

            JSObject result = new JSObject();
            result.put("port", serverSocket.getLocalPort());
            call.resolve(result);

        } catch (IOException e) {
            call.reject("Impossible de démarrer le récepteur.", e);
        }
    }

    private void handleClient(Socket socket) {
        FileOutputStream output = null;

        try {
            BufferedInputStream input =
                    new BufferedInputStream(socket.getInputStream());

            byte[] buffer = new byte[8192];

            String header = readHeader(input);

            if (header == null || !header.startsWith("CHERIF-SEND")) {
                socket.close();
                return;
            }

            File directory =
                    getContext().getExternalFilesDir(null);

            if (directory == null) {
                socket.close();
                return;
            }

            File file = new File(directory, "received_file");

            output = new FileOutputStream(file);

            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            output.flush();
            output.close();
            socket.close();

        } catch (Exception ignored) {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (Exception ignoredAgain) {
            }
        }
    }

    private String readHeader(BufferedInputStream input)
            throws IOException {

        StringBuilder header = new StringBuilder();

        int value;

        while ((value = input.read()) != -1) {
            if (value == '\n') {
                break;
            }

            header.append((char) value);

            if (header.length() > 256) {
                break;
            }
        }

        return header.toString();
    }

    @PluginMethod
    public void stopReceiver(PluginCall call) {
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }

            serverThread = null;
            call.resolve();

        } catch (IOException e) {
            call.reject("Impossible d'arrêter le récepteur.", e);
        }
    }

    private String findLocalAddress() throws Exception {
        Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface =
                    interfaces.nextElement();

            if (!networkInterface.isUp()
                    || networkInterface.isLoopback()) {
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
