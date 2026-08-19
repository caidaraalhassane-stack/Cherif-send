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

            String header = readHeader(input);

            if (header == null || !header.startsWith("CHERIF-SEND")) {
                socket.close();
                return;
            }

            String[] parts = header.split("\\|");

            String fileName = "received_file";
            long fileSize = -1;

            if (parts.length >= 2 && !parts[1].isEmpty()) {
                fileName = new File(parts[1]).getName();
            }

            if (parts.length >= 3) {
                try {
                    fileSize = Long.parseLong(parts[2]);
                } catch (NumberFormatException ignored) {
                }
            }

            File directory = getContext().getExternalFilesDir(null);

            if (directory == null) {
                socket.close();
                return;
            }

            File file = new File(directory, fileName);

            output = new FileOutputStream(file);

            byte[] buffer = new byte[8192];
            long received = 0;
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                received += count;

                if (fileSize > 0 && received >= fileSize) {
                    break;
                }
            }

            output.flush();
            output.close();
            socket.close();

        } catch (Exception
