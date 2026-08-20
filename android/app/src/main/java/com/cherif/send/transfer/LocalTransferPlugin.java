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
        try {
            BufferedInputStream input =
                    new BufferedInputStream(socket.getInputStream());

            String magic = readHeader(input);
            String fileName = readHeader(input);
            String sizeText = readHeader(input);

            if (magic == null || !magic.startsWith("CHERIF-SEND")) {
                socket.close();
                return;
            }

            long fileSize;
            try {
                fileSize = Long.parseLong(sizeText);
            } catch (Exception e) {
                socket.close();
                return;
            }

            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "received_file";
            }

            fileName = new File(fileName).getName();

            android.content.ContentValues values =
                    new android.content.ContentValues();

            values.put(
                    android.provider.MediaStore.Downloads.DISPLAY_NAME,
                    fileName
            );

            values.put(
                    android.provider.MediaStore.Downloads.MIME_TYPE,
                    "application/octet-stream"
            );

            values.put(
                    android.provider.MediaStore.Downloads.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS
            );

            values.put(
                    android.provider.MediaStore.Downloads.IS_PENDING,
                    1
            );

            android.net.Uri uri =
                    getContext().getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            values
                    );

            if (uri == null) {
                socket.close();
                return;
            }

            java.io.OutputStream output =
                    getContext().getContentResolver().openOutputStream(uri);

            if (output == null) {
                socket.close();
                return;
            }

            byte[] buffer = new byte[8192];
            long remaining = fileSize;

            while (remaining > 0) {
                int wanted = (int) Math.min(buffer.length, remaining);
                int count = input.read(buffer, 0, wanted);

                if (count == -1) {
                    break;
                }

                output.write(buffer, 0, count);
                remaining -= count;
            }

            output.flush();
            output.close();
            socket.close();

            values.clear();
            values.put(
                    android.provider.MediaStore.Downloads.IS_PENDING,
                    0
            );

            getContext().getContentResolver().update(
                    uri,
                    values,
                    null,
                    null
            );

            android.content.Intent intent =
                    new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            uri
                    );

            intent.addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            getActivity().runOnUiThread(() -> {
                try {
                    getActivity().startActivity(intent);
                } catch (Exception ignored) {
                }
            });

        } catch (Exception ignored) {
            try {
                socket.close();
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
                return header.toString();
            }

            if (value != '\r') {
                header.append((char) value);
            }

            if (header.length() > 4096) {
                return null;
            }
        }

        return header.length() == 0 ? null : header.toString();
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
